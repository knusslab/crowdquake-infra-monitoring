package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.model.BlkioStatEntry;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
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

    private final Map<String, Long> previousReadBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> previousWriteBytes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> previousRxBytes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> previousTxBytes = new ConcurrentHashMap<>();

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
                    StatsCallbackBlocking callback = new StatsCallbackBlocking();
                    collector.getDockerClient().statsCmd(containerId).exec(callback);
                    Statistics stats = callback.getStats();
                    if (stats == null) return null;

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("type", "container");
                    result.put("containerId", containerId);

                    // CPU 계산
                    Long totalUsage = stats.getCpuStats().getCpuUsage().getTotalUsage();
                    Long systemUsage = stats.getCpuStats().getSystemCpuUsage();
                    Long precTotalUsage = stats.getPreCpuStats().getCpuUsage().getTotalUsage();
                    Long precSystemUsage = stats.getPreCpuStats().getSystemCpuUsage();
                    Long cpuCount = stats.getCpuStats().getOnlineCpus();

                    double cpuUsagePercent = 0.0;
                    if (totalUsage != null && systemUsage != null &&
                            precTotalUsage != null && precSystemUsage != null &&
                            cpuCount != null && systemUsage > precSystemUsage) {
                        double cpuDelta = totalUsage - precTotalUsage;
                        double systemDelta = systemUsage - precSystemUsage;
                        cpuUsagePercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
                    }
                    result.put("cpuUsagePercent", cpuUsagePercent);

                    // 메모리
                    result.put("memoryUsedBytes", stats.getMemoryStats().getUsage());

                    // 디스크 I/O
                    long read = 0, write = 0;
                    List<BlkioStatEntry> ioStats = stats.getBlkioStats().getIoServiceBytesRecursive();
                    if (ioStats != null) {
                        for (BlkioStatEntry io : ioStats) {
                            String op = io.getOp();
                            Long value = io.getValue();
                            if ("Read".equalsIgnoreCase(op)) read += (value != null ? value : 0);
                            else if ("Write".equalsIgnoreCase(op)) write += (value != null ? value : 0);
                        }
                    }
                    result.put("diskReadBytesDelta", read - previousReadBytes.getOrDefault(containerId, 0L));
                    result.put("diskWriteBytesDelta", write - previousWriteBytes.getOrDefault(containerId, 0L));
                    previousReadBytes.put(containerId, read);
                    previousWriteBytes.put(containerId, write);

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


//@Component
//class DataCollectorRunner implements CommandLineRunner {
//    private static final Logger logger = LoggerFactory.getLogger(DataCollectorRunner.class);
//    private final KafkaProducerService kafkaProducerService;
//
//    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//
//    private final Set<String> connectedContainerIds = new HashSet<>();
//    private static final String MONITORING_NETWORK = "monitoring_network";
//    private static final int NETWORK_CHECK_INTERVAL_SECONDS = 30;
//
//    @Autowired
//    public DataCollectorRunner(KafkaProducerService kafkaProducerService) {
//        this.kafkaProducerService = kafkaProducerService;
//    }
//
//    @Override
//    public void run(String... args) {
//
//        DockerStatsCollector collector = new DockerStatsCollector();
//        Gson gson = new Gson();
//
//        Map<String, Statistics> statsMap = collector.getAllContainerStats();
//
//        for (Map.Entry<String, Statistics> entry : statsMap.entrySet()) {
//            String containerId = entry.getKey();
//            Statistics stats = entry.getValue();
//
//            Map<String, Object> result = new HashMap<>();
//            result.put("containerId", containerId);
//            result.put("cpuUsageNano", stats.getCpuStats().getCpuUsage().getTotalUsage());
//            result.put("memoryUsedBytes", stats.getMemoryStats().getUsage());
//
//            // 네트워크도 포함
//            if (stats.getNetworks() != null) {
//                Map<String, Object> net = new HashMap<>();
//                stats.getNetworks().forEach((iface, data) -> {
//                    Map<String, Object> ifaceMap = new HashMap<>();
//                    ifaceMap.put("rxBytes", data.getRxBytes());
//                    ifaceMap.put("txBytes", data.getTxBytes());
//                    net.put(iface, ifaceMap);
//                });
//                result.put("network", net);
//            }
//
//            String json = gson.toJson(result);
//            kafkaProducerService.routeMessageBasedOnType(json);
//        }
//
//
//        //초기값(누적값) 저장 -> 뱐화량 계산을 위해서
//        long prevCpuUsageNano = ContainerResourceMonitor.getCpuUsageNano();
//        long prevDiskReadBytes = ContainerResourceMonitor.getDiskIO()[0];
//        long prevDiskWriteBytes = ContainerResourceMonitor.getDiskIO()[1];
//        Map<String, Long[]> prevNetStats = ContainerResourceMonitor.getNetworkStats();
//
//        //Gson gson = new Gson();
//
//        while (true) {
//            // 자기 자신은 수집과 전송하지 않음 -> 자기 자신을 수집하는 로직은 따로 만들어 보려고 함.
//            String excludeSelf = System.getenv("EXCLUDE_SELF");
//            if ("true".equalsIgnoreCase(excludeSelf)) {
//                logger.info("자기 자신 컨테이너이므로 리소스 수집/전송을 건너뜁니다.");
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                }
//                continue;
//            }
//
//            // 현재 누적값  수집
//            long currCpuUsageNano = ContainerResourceMonitor.getCpuUsageNano();
//            long currDiskReadBytes = ContainerResourceMonitor.getDiskIO()[0];
//            long currDiskWriteBytes = ContainerResourceMonitor.getDiskIO()[1];
//            Map<String, Long[]> currNetStats = ContainerResourceMonitor.getNetworkStats();
//            Map<String, Object> resourceMap = ContainerResourceMonitor.collectContainerResourceRaw();
//
//            // CPU 사용률 계산(1초 기준, 1코어 100%)
//            long deltaCpuNano = currCpuUsageNano - prevCpuUsageNano;
//            double cpuUsagePercent = (deltaCpuNano / 1_000_000_000.0) * 100;
//
//            // 디스크 변화량 계산(읽기/쓰기)
//            long deltaDiskRead = currDiskReadBytes - prevDiskReadBytes;
//            long deltaDiskWrite = currDiskWriteBytes - prevDiskWriteBytes;
//
//            // 네트워크 변화량 및 속도 계산 -> 속도는 1초마다 받아오면 수신 및 송신 바이트와 동일하여 없애도 될 듯
//            Map<String, Map<String, Object>> netDelta = new HashMap<>();
//            for (String iface : currNetStats.keySet()) {
//                Long[] curr = currNetStats.get(iface);
//                Long[] prev = prevNetStats.getOrDefault(iface, new Long[]{curr[0], curr[1]});
//                long deltaRecv = curr[0] - prev[0];
//                long deltaSent = curr[1] - prev[1];
//
//                Map<String, Object> ifaceDelta = new HashMap<>();
//                ifaceDelta.put("rxBytesDelta", deltaRecv);//1초간 수신 바이트
//                ifaceDelta.put("txBytesDelta", deltaSent);//1초간 송신 바이트
//                ifaceDelta.put("rxBps", deltaRecv); // 1초마다 반복이므로 deltaRecv가 Bps
//                ifaceDelta.put("txBps", deltaSent);
//                netDelta.put(iface, ifaceDelta);
//
//                //다음 루프에서 사용할 이전값 갱신
//                prevNetStats.put(iface, curr);
//            }
//
//            // 최종 JSON 생성
//            Map<String, Object> resultJson = new LinkedHashMap<>();
//            resultJson.put("type", resourceMap.get("type"));//컨테이너 타입
//            resultJson.put("containerId", resourceMap.get("containerId"));//컨테이너 아이디
//            resultJson.put("cpuUsagePercent", cpuUsagePercent);//CPU 사용률(%)
//            resultJson.put("memoryUsedBytes", resourceMap.get("memoryUsedBytes"));//메모리 사용량(바이트)
//            resultJson.put("diskReadBytesDelta", deltaDiskRead);//디스크 읽기 변화량(바이트)
//            resultJson.put("diskWriteBytesDelta", deltaDiskWrite);//디스크 쓰기 변화량(바이트)
//            resultJson.put("networkDelta", netDelta);//네트워크 인터페이스별 변화량/속도
//
//            String jsonPayload = gson.toJson(resultJson);
//
//            //System.out.println("=== 컨테이너 리소스 변화량 수집 결과 ===");
//            System.out.println(jsonPayload);
//
//            // Kafka로 전송
//            kafkaProducerService.routeMessageBasedOnType(jsonPayload);
//
//            // 이전값 갱신
//            prevCpuUsageNano = currCpuUsageNano;
//            prevDiskReadBytes = currDiskReadBytes;
//            prevDiskWriteBytes = currDiskWriteBytes;
//            prevNetStats = currNetStats;
//
//            //1초 대기 후 반복
//            try {
//                Thread.sleep(1000); // 1초마다 반복
//            } catch (InterruptedException e) {
//                System.out.println("스레드가 인터럽트되었습니다.");
//            }
//        }
//    }
//
//
//    // 애플리케이션 종료 시 스케줄러 정리
//    @PreDestroy
//    public void destroy() {
//        scheduler.shutdown();
//        try {
//            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
//                scheduler.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            scheduler.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//    }
//
//}
