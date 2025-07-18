package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.InvocationBuilder;
import com.google.gson.Gson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.BlkioStatEntry;


@SpringBootApplication
public class DataCollectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataCollectorApplication.class, args);
    }
}

@Component
class KafkaProducerRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerRunner.class);

    @Value("${BOOTSTRAP_SERVER}")
    private String kafkaBootstrapServer;

    @Value("${KAFKA_TOPIC_NAME}")
    private String kafkaTopic;

    @Value("${HOST_ID:host-001}")
    private String hostId;

    @Value("${HOST_NAME:localhost}")
    private String hostName;

    private final MachineResourceMonitor hostMonitor = new MachineResourceMonitor();
    private final DockerStatsCollector dockerCollector = new DockerStatsCollector();

    // 호스트 delta 계산용
    private long prevDiskReadBytes = 0;
    private long prevDiskWriteBytes = 0;
    private final Map<String, Long> prevNetRecv = new HashMap<>();
    private final Map<String, Long> prevNetSent = new HashMap<>();

    // 컨테이너 delta 계산용
    private final Map<String, Long> prevContainerDiskRead = new ConcurrentHashMap<>();
    private final Map<String, Long> prevContainerDiskWrite = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> prevContainerNet = new ConcurrentHashMap<>();
    private final Map<String, Long> prevContainerTotalUsage = new ConcurrentHashMap<>();
    private final Map<String, Long> prevContainerSystemUsage = new ConcurrentHashMap<>();

    // 컨테이너 병렬 수집용
    private final ExecutorService pool = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    @Override
    public void run(String... args) throws Exception {
        //run 메서드는 애플리케이션 시작 시 실행되며, Kafka 프로듀서를 통해 주기적으로 리소스 데이터를 수집 및 전송함.
        Properties props = buildKafkaProperties();
        // JSON 출력 시 보기 좋게 들여쓰기를 활성화한 ObjectMapper 생성
        ObjectMapper prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Kafka 프로듀서 생성 및 try-with-resources를 통해 자동 자원 해제 처리
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            while (true) {
                // 1. 호스트 정보 수집 및 delta 계산
                Map<String, Object> hostData = collectHostResource();

                // 2. 컨테이너 정보 수집 및 delta 계산
                Map<String, Map<String, Object>> containersData = collectAllContainerResource();

                // 3. 통합 JSON 조립
                hostData.put("containers", containersData);

                // 4. JSON 문자열로 변환 (pretty print) 후 콘솔에 출력
                String prettyJson = prettyMapper.writeValueAsString(hostData);
                System.out.println(prettyJson);

                //카프카에 메시지 전송
                sendKafkaRecord(producer, kafkaTopic, prettyJson);


            }
        }
    }

    //카프카 설정을 구성하여 properties 객체로 변환
    private Properties buildKafkaProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapServer);//카프카 브로커 주소
        props.put("acks", "all");//모든 리더가 응답할 때까지 기다림
        props.put("retries", 0);//전송 실패 시 재시도 횟수

        //16384인 이유: 16KB임.
        // 카프카 프로듀서는 레코드를 보낼 때 한번에 여러 메시지를 묶어서 보내는데, 이때 묶을 수 있는 최대 크기가 이 설정임
        //기본값도 16384로 되어 있음.
        props.put("batch.size", 16384);//배치 크기 설정(바이트 단위)

        //카프카는 배치 전송을 위해 잠시 기다릴 수 있는데, 1ms는 거의 즉시 전송하겠다는 의미
        //지연 줄이기 위해서 사용
        props.put("linger.ms", 1);//배치 전송 전 대기 시간(1ms)

        //32MB임
        //프로듀서가 메시지를 전송하기 전에 클라이언트 메모리에 보관할 수 있는 총 메시지 크기임/
        //기본값도 이 정도
        //이보다 더 커지면 send()가 블로킹 되거나 예외가 발생할 수 있음.
        props.put("buffer.memory", 33554432);//프로듀서 버터 메모리 크기
        props.put("key.serializer", StringSerializer.class.getName());//메시지 키 직렬화 방식
        props.put("value.serializer", StringSerializer.class.getName());//메시지 값 직렬화 방식
        return props;
    }

    //카프카 메시지를 생성하고 전송, 결과를 콜백으로 처립
    private void sendKafkaRecord(Producer<String, String> producer, String topic, String message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);

        //비동기 전송 및 전송 결과 처리 콜백
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("Kafka 전송 실패: " + exception.getMessage());
            } else {
                System.out.println("Kafka 전송 성공: " + metadata.topic() + " offset=" + metadata.offset());
            }
        });
    }



    // 호스트 리소스 수집 및 delta 계산
    private Map<String, Object> collectHostResource() {
        //호스트 리소스 정보를 json 문자열로 읽어옴
        String jsonStr = hostMonitor.getResourcesAsJson();
        Map<String, Object> resourceMap = new Gson().fromJson(jsonStr, Map.class);

        // disk delta
        //이전값과 현재값 차이를 계산
        long currDiskReadBytes = ((Number) resourceMap.get("diskReadBytes")).longValue();
        long currDiskWriteBytes = ((Number) resourceMap.get("diskWriteBytes")).longValue();
        long deltaDiskRead = currDiskReadBytes - prevDiskReadBytes;
        long deltaDiskWrite = currDiskWriteBytes - prevDiskWriteBytes;
        prevDiskReadBytes = currDiskReadBytes;
        prevDiskWriteBytes = currDiskWriteBytes;

        // network delta
        //이전값과 현재값 차이를 계산
        Map<String, Object> netInfo = (Map<String, Object>) resourceMap.get("network");
        Map<String, Map<String, Object>> netDelta = computeHostNetworkDelta(netInfo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "host");
        result.put("hostId", resourceMap.get("hostId"));
        try {
            hostName = Files.readString(Paths.get("/host/etc/hostname")).trim();
        } catch (Exception e) {
            hostName = "unknown";
        }
        result.put("name", hostName);
        result.put("timeStamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        result.put("cpuUsagePercent", resourceMap.get("cpuUsagePercent"));
        result.put("memoryUsedBytes", resourceMap.get("memoryUsedBytes"));
        result.put("diskReadBytesDelta", deltaDiskRead);
        result.put("diskWriteBytesDelta", deltaDiskWrite);
        result.put("networkDelta", netDelta);//각 네트워크 인터페이스별 delta
        result.put("temperatures", resourceMap.get("temperatures"));

        return result;
    }

    //네트워크 인터페이스별로 delta값을 계산해서 반환
    private Map<String, Map<String, Object>> computeHostNetworkDelta(Map<String, Object> netInfo) {
        Map<String, Map<String, Object>> netDelta = new HashMap<>();
        for (String iface : netInfo.keySet()) {
            Map<String, Object> ifaceInfo = (Map<String, Object>) netInfo.get(iface);
            //각 인터페이스의 현재 수신 및 송신 바이트
            long currRecv = ((Number) ifaceInfo.get("bytesReceived")).longValue();
            long currSent = ((Number) ifaceInfo.get("bytesSent")).longValue();
            //이전 값이 없으면 curr로 ㅊ초기화
            long prevRecv = prevNetRecv.getOrDefault(iface, currRecv);
            long prevSent = prevNetSent.getOrDefault(iface, currSent);

            Map<String, Object> delta = new HashMap<>();
            delta.put("rxBytesDelta", currRecv - prevRecv);
            delta.put("txBytesDelta", currSent - prevSent);
            netDelta.put(iface, delta);

            //다음 계산을 위해 현재 값 저장
            prevNetRecv.put(iface, currRecv);
            prevNetSent.put(iface, currSent);
        }
        return netDelta;
    }

    // 모든 컨테이너 리소스 수집 및 delta 계산
    private Map<String, Map<String, Object>> collectAllContainerResource() {
        List<Container> containers = dockerCollector.listAllContainers();
        Map<String, Map<String, Object>> containersMap = new ConcurrentHashMap<>();

        //현재 살아있는 컨테이너 ID 목록 기록
        Set<String> currentContainerIds = containers.stream()
                .map(Container::getId)
                .collect(Collectors.toSet());

        // 이전 상태 Map에서 더 이상 존재하지 않는 containerId 제거
        prevContainerDiskRead.keySet().removeIf(id -> !currentContainerIds.contains(id));
        prevContainerDiskWrite.keySet().removeIf(id -> !currentContainerIds.contains(id));
        prevContainerNet.keySet().removeIf(id -> !currentContainerIds.contains(id));
        prevContainerTotalUsage.keySet().removeIf(id -> !currentContainerIds.contains(id));
        prevContainerSystemUsage.keySet().removeIf(id -> !currentContainerIds.contains(id));

        //멀티스레드로 컨테이너 통계 병렬 수집
        List<Future<?>> futures = new ArrayList<>();
        for (Container container : containers) {
            futures.add(pool.submit(() -> {
                Map<String, Object> stats = collectContainerStats(container);
                containersMap.put(container.getId(), stats);
            }));
        }
        // 병렬 수집 완료 대기
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        return containersMap;
    }

    // 컨테이너별 리소스 수집 및 delta 계산
    private Map<String, Object> collectContainerStats(Container container) {
        try (InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>()) {
            dockerCollector.getDockerClient().statsCmd(container.getId()).exec(callback);
            Statistics stats = callback.awaitResult();

            return calculateContainerStats(container, stats);
        } catch (Exception e) {
            //통계 수집 실패하면 빈 결과 반환
            return new LinkedHashMap<>();
        }
    }

    //실제 지표  계산
    private Map<String, Object> calculateContainerStats(Container container, Statistics stats) {
        Map<String, Object> result = new LinkedHashMap<>();
        String containerName = Arrays.stream(container.getNames()).findFirst().orElse("unknown");
        result.put("name", containerName);

        // CPU 사용률(%)
        double cpuUsagePercent = calculateCpuUsage(container.getId(), stats);
        result.put("cpuUsagePercent", cpuUsagePercent);

        // Memory 사용량(바이트)
        long memoryUsedBytes = calculateMemoryUsage(stats);
        result.put("memoryUsedBytes", memoryUsedBytes);

        // Disk I/O delta
        Map<String, Long> diskDeltas = calculateDiskDelta(container.getId(), stats);
        result.put("diskReadBytesDelta", diskDeltas.getOrDefault("readDelta", 0L));
        result.put("diskWriteBytesDelta", diskDeltas.getOrDefault("writeDelta", 0L));

        // Network delta
        Map<String, Object> networkDelta = calculateNetworkDelta(container.getId(), stats);
        result.put("networkDelta", networkDelta);

        return result;
    }

    //cpu 사용률 계산
    //이전 상태와 비교해서 delta로 계산
    private double calculateCpuUsage(String containerId, Statistics stats) {
        Long totalUsage = stats.getCpuStats().getCpuUsage().getTotalUsage();
        Long systemUsage = stats.getCpuStats().getSystemCpuUsage();
        Long cpuCount = stats.getCpuStats().getOnlineCpus();

        //이전 값 없으면 현재로 대입
        long prevTotal = prevContainerTotalUsage.getOrDefault(containerId, totalUsage != null ? totalUsage : 0L);
        long prevSystem = prevContainerSystemUsage.getOrDefault(containerId, systemUsage != null ? systemUsage : 0L);

        double cpuUsagePercent = 0.0;

        if (cpuUsageCondition(totalUsage, prevTotal, systemUsage, prevSystem, cpuCount, containerId)) {
            double cpuDelta = totalUsage - prevTotal;
            double systemDelta = systemUsage - prevSystem;
            cpuUsagePercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
        }

        //다음 계산을 위해 현재값 저장
        prevContainerTotalUsage.put(containerId, totalUsage != null ? totalUsage : 0L);
        prevContainerSystemUsage.put(containerId, systemUsage != null ? systemUsage : 0L);

        return cpuUsagePercent;
    }

    /**
     * 컨테이너의 CPU 사용률을 계산하기 전, 필요한 조건이 모두 만족하는지 확인하는 함수.
     *
     * 검사하는 조건(모두 만족해야 정상 계산 가능):
     *   1. totalUsage(누적 컨테이너 CPU)이 null이 아님
     *   2. systemUsage(누적 시스템 CPU)가 null이 아님
     *   3. cpuCount(코어 수)가 null이 아님
     *   4. systemUsage가 prevSystem 보다 증가했음 (CPU 누적량이 정상적으로 커져야 함)
     *   5. totalUsage가 prevTotal 보다 증가했음 (컨테이너 누적 사용량도 증가해야 정상)
     *   6. cpuCount가 1 이상 양의 값이어야 함
     *
     * 반환값:
     *   - true: 계산 가능한 상태(정상)
     *   - false: 위 조건 중 하나 이상이 실패 (계산하지 않음, 로그 등으로 추가 분석 필요)
     */
    private boolean cpuUsageCondition(
            Long totalUsage, long prevTotal,
            Long systemUsage, long prevSystem,
            Long cpuCount, String containerId
    ) {
        boolean condTotalUsageNotNull   = totalUsage != null;
        boolean condSystemUsageNotNull  = systemUsage != null;
        boolean condCpuCountNotNull     = cpuCount != null;
        boolean condSystemUsageInc      = condSystemUsageNotNull && systemUsage > prevSystem;
        boolean condTotalUsageInc       = condTotalUsageNotNull && totalUsage > prevTotal;
        boolean condCpuCountPositive    = condCpuCountNotNull && cpuCount > 0;

        if (!condTotalUsageNotNull) {
            // 누적 CPU 사용량(totalUsage)이 null인 경우
            logger.debug("totalUsage is null for container: {}", containerId);
        }
        if (!condSystemUsageNotNull) {
            // 시스템 전체 CPU 사용량(systemUsage)이 null인 경우
            logger.debug("systemUsage is null for container: {}", containerId);
        }
        if (!condCpuCountNotNull) {
            // CPU 코어 개수(cpuCount)가 null인 경우
            logger.debug("cpuCount is null for container: {}", containerId);
        }
        if (condSystemUsageNotNull && !condSystemUsageInc) {
            // systemUsage는 존재하지만 이전 값보다 증가하지 않은 경우
            logger.debug("systemUsage is not increased: {} <= {} (containerId: {})", systemUsage, prevSystem, containerId);
        }
        if (condTotalUsageNotNull && !condTotalUsageInc) {
            // totalUsage는 존재하지만 이전 값보다 증가하지 않은 경우
            logger.debug("totalUsage is not increased: {} <= {} (containerId: {})", totalUsage, prevTotal, containerId);
        }
        if (condCpuCountNotNull && !condCpuCountPositive) {
            // cpuCount는 존재하지만 0이거나 음수인 경우
            logger.debug("cpuCount is not positive: {} (containerId: {})", cpuCount, containerId);
        }

        return condTotalUsageNotNull &&
                condSystemUsageNotNull &&
                condCpuCountNotNull &&
                condSystemUsageInc &&
                condTotalUsageInc &&
                condCpuCountPositive;
    }

    //메모리 사용량 계산
    private long calculateMemoryUsage(Statistics stats) {
        try {
            if (stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null) {
                return stats.getMemoryStats().getUsage();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    //disk I/o delta 계산
    private Map<String, Long> calculateDiskDelta(String containerId, Statistics stats) {
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
        long prevRead = prevContainerDiskRead.getOrDefault(containerId, read);
        long prevWrite = prevContainerDiskWrite.getOrDefault(containerId, write);

        Map<String, Long> delta = new HashMap<>();
        delta.put("readDelta", read - prevRead);
        delta.put("writeDelta", write - prevWrite);

        //다음 계산을 위해 이전 값 저장
        prevContainerDiskRead.put(containerId, read);
        prevContainerDiskWrite.put(containerId, write);

        return delta;
    }

    //컨테이너 네트워크 delta 계산
    private Map<String, Object> calculateNetworkDelta(String containerId, Statistics stats) {
        Map<String, Object> networkDelta = new LinkedHashMap<>();
        if (stats.getNetworks() != null) {
            Map<String, Long> prevNet = prevContainerNet.getOrDefault(containerId, new ConcurrentHashMap<>());
            stats.getNetworks().forEach((iface, net) -> {
                long rx = net.getRxBytes();
                long tx = net.getTxBytes();
                long prevRx = prevNet.getOrDefault(iface + ":rx", rx);
                long prevTx = prevNet.getOrDefault(iface + ":tx", tx);

                Map<String, Object> ifaceData = new LinkedHashMap<>();
                ifaceData.put("rxBytesDelta", rx - prevRx);
                ifaceData.put("txBytesDelta", tx - prevTx);
                networkDelta.put(iface, ifaceData);

                //다음 계산을 위해 현재값 저장
                prevNet.put(iface + ":rx", rx);
                prevNet.put(iface + ":tx", tx);
            });
            prevContainerNet.put(containerId, prevNet);
        }
        return networkDelta;
    }
}