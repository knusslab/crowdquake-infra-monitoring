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

//컨테이너 내부에서 주기적으로 리소스(CPU, 메모리, 디스크, 네트워크 등) 사용량을 수집
//카프카로 전송
@SpringBootApplication(scanBasePackages = "kr.cs.interdata")
public class DataCollectorApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DataCollectorApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}

// 변경된 DataCollectorRunner
@Component
class DataCollectorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectorRunner.class);

    private final KafkaProducerService kafkaProducerService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, Map<String, Long>> previousRxBytes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> previousTxBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousDiskReadBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousDiskWriteBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousTotalUsage = new ConcurrentHashMap<>();
    private final Map<String, Long> previousSystemUsage = new ConcurrentHashMap<>();


    private final ExecutorService pool = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    @Autowired
    public DataCollectorRunner(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void run(String... args) {
        scheduler.scheduleAtFixedRate(this::monitorContainersIndividually, 0, 1, TimeUnit.SECONDS);
    }

    private void monitorContainersIndividually() {
        DockerStatsCollector collector = new DockerStatsCollector();
        List<Container> containers = collector.listAllContainers();
        Gson gson = new Gson();

        for (Container container : containers) {
            pool.submit(() -> {
                try {
                    String containerId = container.getId();

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

                    // CPU 계산
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

                    // cpu 이전 값 갱신
                    previousTotalUsage.put(containerId, totalUsage != null ? totalUsage : 0L);
                    previousSystemUsage.put(containerId, systemUsage != null ? systemUsage : 0L);


                    // 메모리
                    //result.put("memoryUsedBytes", stats.getMemoryStats().getUsage());
                    Long memoryUsedBytes = 0L;
                    try {
                        if (stats != null && stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null) {
                            memoryUsedBytes = stats.getMemoryStats().getUsage();
                        }
                    } catch (Exception e) {
                        // 예외 발생 시에도 0L 유지
                        logger.error("<UNK> <UNK> <UNK> <UNK>", e);
                    }
                    result.put("memoryUsedBytes", memoryUsedBytes.doubleValue());

                    // 디스크 I/O
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

                    // 이전 값 가져오기
                    long prevRead = previousDiskReadBytes.getOrDefault(containerId, 0L);
                    long prevWrite = previousDiskWriteBytes.getOrDefault(containerId, 0L);

                    // delta 계산
                    long diskReadBytesDelta = read - prevRead;
                    long diskWriteBytesDelta = write - prevWrite;

                    result.put("diskReadBytesDelta", diskReadBytesDelta);
                    result.put("diskWriteBytesDelta", diskWriteBytesDelta);

                    // 이전 값 갱신
                    previousDiskReadBytes.put(containerId, read);
                    previousDiskWriteBytes.put(containerId, write);

                    // 네트워크
                    Map<String, Object> networkDelta = new LinkedHashMap<>();
                    if (stats.getNetworks() != null) {
                        stats.getNetworks().forEach((iface, net) -> {
                            long rx = net.getRxBytes();
                            long tx = net.getTxBytes();
                            Map<String, Long> prev = previousRxBytes.getOrDefault(containerId, new ConcurrentHashMap<>());

                            long rxDelta = rx - prev.getOrDefault(iface + ":rx", 0L);
                            long txDelta = tx - prev.getOrDefault(iface + ":tx", 0L);

                            Map<String, Object> ifaceData = new LinkedHashMap<>();
                            ifaceData.put("rxBytesDelta", rxDelta);
                            ifaceData.put("txBytesDelta", txDelta);

                            networkDelta.put(iface, ifaceData);

                            prev.put(iface + ":rx", rx);
                            prev.put(iface + ":tx", tx);
                            previousRxBytes.put(containerId, prev);
                        });
                    }
                    result.put("networkDelta", networkDelta);

                    String json = gson.toJson(result);
                    logger.info("[MONITOR] {}", json);
                    kafkaProducerService.routeMessageBasedOnType(json);
                } catch (Exception e) {
                    logger.error("컨테이너 stats 처리 오류", e);
                }
                return null;
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        pool.shutdown();
    }
}
