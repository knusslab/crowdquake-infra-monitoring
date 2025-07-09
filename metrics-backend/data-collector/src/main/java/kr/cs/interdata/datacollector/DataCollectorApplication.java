package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.model.BlkioStatEntry;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.InvocationBuilder;
import com.google.gson.Gson;
import jakarta.annotation.PreDestroy;
import kr.cs.interdata.producer.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * DataCollectorApplication은 Spring Boot CLI 애플리케이션으로,
 * 컨테이너의 리소스 사용량을 주기적으로 수집하여 Kafka로 전송하는 역할을 한다.
 */
@SpringBootApplication(scanBasePackages = "kr.cs.interdata")
public class DataCollectorApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DataCollectorApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE); // 웹서버 없이 CLI 실행
        app.run(args);
    }
}

/**
 * DataCollectorRunner는 Spring Boot가 시작될 때 실행되며,
 * 주기적으로 Docker 컨테이너의 리소스 사용량을 수집하고 Kafka로 전송한다.
 */
@Component
class DataCollectorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectorRunner.class);

    private final KafkaProducerService kafkaProducerService;

    // 스케줄러: 주기적으로 모니터링 실행
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 이전 상태 저장용 맵: delta 계산을 위함
    private final Map<String, Map<String, Long>> previousNetworkBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousDiskReadBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousDiskWriteBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousTotalUsage = new ConcurrentHashMap<>();
    private final Map<String, Long> previousSystemUsage = new ConcurrentHashMap<>();

    // 컨테이너별 병렬처리를 위한 스레드 풀
    private final ExecutorService pool = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    @Autowired
    public DataCollectorRunner(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Spring Boot 실행 시 호출되어 모니터링을 시작한다.
     *
     * @param args CLI 인자
     */
    @Override
    public void run(String... args) {
        // 1초 간격으로 모니터링 수행
        scheduler.scheduleAtFixedRate(this::monitorContainersIndividually, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 모든 컨테이너에 대해 비동기적으로 stats 수집 및 Kafka 전송
     */
    private void monitorContainersIndividually() {
        DockerStatsCollector collector = new DockerStatsCollector();
        List<Container> containers = collector.listAllContainers();
        Gson gson = new Gson();

        for (Container container : containers) {
            pool.submit(() -> {
                try {
                    String containerId = container.getId();

                    // 비동기적으로 stats 수집
                    InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>();
                    collector.getDockerClient().statsCmd(containerId).exec(callback);

                    Statistics stats;
                    try {
                        stats = callback.awaitResult();
                        callback.close();
                    } catch (RuntimeException | IOException e) {
                        logger.error("컨테이너 stats 처리 오류", e);
                        return null;
                    }

                    if (stats == null) return null;

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("type", "container");
                    result.put("containerId", containerId);

                    /** -------------------- CPU -------------------- */
                    Long totalUsage = stats.getCpuStats().getCpuUsage().getTotalUsage();
                    Long systemUsage = stats.getCpuStats().getSystemCpuUsage();
                    Long cpuCount = stats.getCpuStats().getOnlineCpus();

                    long prevTotal = previousTotalUsage.getOrDefault(containerId, 0L);
                    long prevSystem = previousSystemUsage.getOrDefault(containerId, 0L);

                    double cpuUsagePercent = 0.0;
                    if (totalUsage != null && systemUsage != null && cpuCount != null &&
                            systemUsage > prevSystem && totalUsage > prevTotal && cpuCount > 0) {
                        double cpuDelta = totalUsage - prevTotal;
                        double systemDelta = systemUsage - prevSystem;
                        cpuUsagePercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
                    }

                    result.put("cpuUsagePercent", cpuUsagePercent);

                    // 이전 값 갱신
                    previousTotalUsage.put(containerId, totalUsage != null ? totalUsage : 0L);
                    previousSystemUsage.put(containerId, systemUsage != null ? systemUsage : 0L);

                    /** -------------------- Memory -------------------- */
                    Long memoryUsedBytes = 0L;
                    try {
                        if (stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null) {
                            memoryUsedBytes = stats.getMemoryStats().getUsage();
                        }
                    } catch (Exception e) {
                        logger.error("메모리 사용량 수집 실패", e);
                    }
                    result.put("memoryUsedBytes", memoryUsedBytes.doubleValue());

                    /** -------------------- Disk I/O -------------------- */
                    long read = 0, write = 0;
                    List<BlkioStatEntry> ioStats = stats.getBlkioStats().getIoServiceBytesRecursive();
                    if (ioStats != null) {
                        for (BlkioStatEntry entry : ioStats) {
                            String op = entry.getOp();
                            Long value = entry.getValue();

                            if ("Read".equalsIgnoreCase(op)) {
                                read += (value != null ? value : 0);
                            } else if ("Write".equalsIgnoreCase(op)) {
                                write += (value != null ? value : 0);
                            }
                        }
                    }

                    long prevRead = previousDiskReadBytes.getOrDefault(containerId, 0L);
                    long prevWrite = previousDiskWriteBytes.getOrDefault(containerId, 0L);

                    result.put("diskReadBytesDelta", read - prevRead);
                    result.put("diskWriteBytesDelta", write - prevWrite);

                    previousDiskReadBytes.put(containerId, read);
                    previousDiskWriteBytes.put(containerId, write);

                    /** -------------------- Network -------------------- */
                    Map<String, Object> networkDelta = new LinkedHashMap<>();
                    if (stats.getNetworks() != null) {
                        stats.getNetworks().forEach((iface, net) -> {
                            long rx = net.getRxBytes();
                            long tx = net.getTxBytes();
                            Map<String, Long> prev = previousNetworkBytes.getOrDefault(containerId, new ConcurrentHashMap<>());

                            long rxDelta = rx - prev.getOrDefault(iface + ":rx", 0L);
                            long txDelta = tx - prev.getOrDefault(iface + ":tx", 0L);

                            Map<String, Object> ifaceData = new LinkedHashMap<>();
                            ifaceData.put("rxBytesDelta", rxDelta);
                            ifaceData.put("txBytesDelta", txDelta);

                            networkDelta.put(iface, ifaceData);

                            // 이전 값 갱신
                            prev.put(iface + ":rx", rx);
                            prev.put(iface + ":tx", tx);
                            previousNetworkBytes.put(containerId, prev);
                        });
                    }
                    result.put("networkDelta", networkDelta);

                    // JSON 직렬화 후 Kafka 전송
                    String json = gson.toJson(result);
                    logger.info("[MONITOR] {}", json);
                    kafkaProducerService.routeMessageBasedOnType(json);

                } catch (Exception e) {
                    logger.error("컨테이너 stats 처리 중 예외 발생", e);
                }
                return null;
            });
        }
    }

    /**
     * 어플리케이션 종료 시 리소스 해제
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        pool.shutdown();
    }
}
