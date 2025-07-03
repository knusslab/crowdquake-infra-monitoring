package kr.cs.interdata.datacollector;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//컨테이너 내부에서 주기적으로 리소스(CPU, 메모리, 디스크, 네트워크 등) 사용량을 수집
//카프카로 전송
@SpringBootApplication(scanBasePackages = "kr.cs.interdata")
public class DataCollectorApplication {
    public static void main(String[] args) {
        //스프링 부트 애플리케이션 실행
        SpringApplication app = new SpringApplication(DataCollectorApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}

@Component
class DataCollectorRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DataCollectorRunner.class);
    private final KafkaProducerService kafkaProducerService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> connectedContainerIds = new HashSet<>();
    private static final String MONITORING_NETWORK = "monitoring_network";
    private static final int NETWORK_CHECK_INTERVAL_SECONDS = 30;

    @Autowired
    public DataCollectorRunner(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void run(String... args) {


        //초기값(누적값) 저장 -> 뱐화량 계산을 위해서
        long prevCpuUsageNano = ContainerResourceMonitor.getCpuUsageNano();
        long prevDiskReadBytes = ContainerResourceMonitor.getDiskIO()[0];
        long prevDiskWriteBytes = ContainerResourceMonitor.getDiskIO()[1];
        Map<String, Long[]> prevNetStats = ContainerResourceMonitor.getNetworkStats();

        Gson gson = new Gson();

        while (true) {
            // 자기 자신은 수집과 전송하지 않음 -> 자기 자신을 수집하는 로직은 따로 만들어 보려고 함.
            String excludeSelf = System.getenv("EXCLUDE_SELF");
            if ("true".equalsIgnoreCase(excludeSelf)) {
                logger.info("자기 자신 컨테이너이므로 리소스 수집/전송을 건너뜁니다.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                continue;
            }

            // 현재 누적값  수집
            long currCpuUsageNano = ContainerResourceMonitor.getCpuUsageNano();
            long currDiskReadBytes = ContainerResourceMonitor.getDiskIO()[0];
            long currDiskWriteBytes = ContainerResourceMonitor.getDiskIO()[1];
            Map<String, Long[]> currNetStats = ContainerResourceMonitor.getNetworkStats();
            Map<String, Object> resourceMap = ContainerResourceMonitor.collectContainerResourceRaw();

            // CPU 사용률 계산(1초 기준, 1코어 100%)
            long deltaCpuNano = currCpuUsageNano - prevCpuUsageNano;
            double cpuUsagePercent = (deltaCpuNano / 1_000_000_000.0) * 100;

            // 디스크 변화량 계산(읽기/쓰기)
            long deltaDiskRead = currDiskReadBytes - prevDiskReadBytes;
            long deltaDiskWrite = currDiskWriteBytes - prevDiskWriteBytes;

            // 네트워크 변화량 및 속도 계산 -> 속도는 1초마다 받아오면 수신 및 송신 바이트와 동일하여 없애도 될 듯
            Map<String, Map<String, Object>> netDelta = new HashMap<>();
            for (String iface : currNetStats.keySet()) {
                Long[] curr = currNetStats.get(iface);
                Long[] prev = prevNetStats.getOrDefault(iface, new Long[]{curr[0], curr[1]});
                long deltaRecv = curr[0] - prev[0];
                long deltaSent = curr[1] - prev[1];

                Map<String, Object> ifaceDelta = new HashMap<>();
                ifaceDelta.put("rxBytesDelta", deltaRecv);//1초간 수신 바이트
                ifaceDelta.put("txBytesDelta", deltaSent);//1초간 송신 바이트
                ifaceDelta.put("rxBps", deltaRecv); // 1초마다 반복이므로 deltaRecv가 Bps
                ifaceDelta.put("txBps", deltaSent);
                netDelta.put(iface, ifaceDelta);

                //다음 루프에서 사용할 이전값 갱신
                prevNetStats.put(iface, curr);
            }

            // 최종 JSON 생성
            Map<String, Object> resultJson = new LinkedHashMap<>();
            resultJson.put("type", resourceMap.get("type"));//컨테이너 타입
            resultJson.put("containerId", resourceMap.get("containerId"));//컨테이너 아이디
            resultJson.put("cpuUsagePercent", cpuUsagePercent);//CPU 사용률(%)
            resultJson.put("memoryUsedBytes", resourceMap.get("memoryUsedBytes"));//메모리 사용량(바이트)
            resultJson.put("diskReadBytesDelta", deltaDiskRead);//디스크 읽기 변화량(바이트)
            resultJson.put("diskWriteBytesDelta", deltaDiskWrite);//디스크 쓰기 변화량(바이트)
            resultJson.put("networkDelta", netDelta);//네트워크 인터페이스별 변화량/속도

            String jsonPayload = gson.toJson(resultJson);

            //System.out.println("=== 컨테이너 리소스 변화량 수집 결과 ===");
            System.out.println(jsonPayload);

            // Kafka로 전송
            kafkaProducerService.routeMessageBasedOnType(jsonPayload);

            // 이전값 갱신
            prevCpuUsageNano = currCpuUsageNano;
            prevDiskReadBytes = currDiskReadBytes;
            prevDiskWriteBytes = currDiskWriteBytes;
            prevNetStats = currNetStats;

            //1초 대기 후 반복
            try {
                Thread.sleep(1000); // 1초마다 반복
            } catch (InterruptedException e) {
                System.out.println("스레드가 인터럽트되었습니다.");
            }
        }
    }


    // 애플리케이션 종료 시 스케줄러 정리
    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
