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

@SpringBootApplication
public class DataCollectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataCollectorApplication.class, args);
    }
}

@Component
class KafkaProducerRunner implements CommandLineRunner {

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
        String jsonStr = hostMonitor.getResourcesAsJson();
        Map<String, Object> resourceMap = new Gson().fromJson(jsonStr, Map.class);

        // disk delta
        long currDiskReadBytes = ((Number) resourceMap.get("diskReadBytes")).longValue();
        long currDiskWriteBytes = ((Number) resourceMap.get("diskWriteBytes")).longValue();
        long deltaDiskRead = currDiskReadBytes - prevDiskReadBytes;
        long deltaDiskWrite = currDiskWriteBytes - prevDiskWriteBytes;
        prevDiskReadBytes = currDiskReadBytes;
        prevDiskWriteBytes = currDiskWriteBytes;

        // network delta
        Map<String, Object> netInfo = (Map<String, Object>) resourceMap.get("network");
        Map<String, Map<String, Object>> netDelta = new HashMap<>();
        for (String iface : netInfo.keySet()) {
            Map<String, Object> ifaceInfo = (Map<String, Object>) netInfo.get(iface);
            long currRecv = ((Number) ifaceInfo.get("bytesReceived")).longValue();
            long currSent = ((Number) ifaceInfo.get("bytesSent")).longValue();
            long prevRecv = prevNetRecv.getOrDefault(iface, currRecv);
            long prevSent = prevNetSent.getOrDefault(iface, currSent);

            long deltaRecv = currRecv - prevRecv;
            long deltaSent = currSent - prevSent;

            Map<String, Object> ifaceDelta = new HashMap<>();
            ifaceDelta.put("rxBytesDelta", deltaRecv);
            ifaceDelta.put("txBytesDelta", deltaSent);
            netDelta.put(iface, ifaceDelta);

            prevNetRecv.put(iface, currRecv);
            prevNetSent.put(iface, currSent);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "host");
        //result.put("hostId", hostId);
        result.put("hostId", resourceMap.get("hostId"));
        //result.put("name", hostName);
        try {
            hostName = Files.readString(Paths.get("/host/etc/hostname")).trim();
            //hostName = Files.readString(Paths.get("/host/proc/sys/kernel/hostname")).trim();
        } catch (Exception e) {
            hostName = "unknown";
        }
        result.put("name", hostName);
        result.put("timeStamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        result.put("cpuUsagePercent", resourceMap.get("cpuUsagePercent"));
        result.put("memoryUsedBytes", resourceMap.get("memoryUsedBytes"));
        result.put("diskReadBytesDelta", deltaDiskRead);
        result.put("diskWriteBytesDelta", deltaDiskWrite);
        result.put("networkDelta", netDelta);
        result.put("temperatures", resourceMap.get("temperatures"));

        return result;
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
        Statistics stats = null;
        try {
            InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>();
            dockerCollector.getDockerClient().statsCmd(container.getId()).exec(callback);
            stats = callback.awaitResult();
            callback.close();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", Arrays.stream(container.getNames()).findFirst().orElse("unknown"));

        // CPU
        Long totalUsage = stats.getCpuStats().getCpuUsage().getTotalUsage();
        Long systemUsage = stats.getCpuStats().getSystemCpuUsage();
        Long cpuCount = stats.getCpuStats().getOnlineCpus();

        long prevTotal = prevContainerTotalUsage.getOrDefault(container.getId(), totalUsage != null ? totalUsage : 0L);
        long prevSystem = prevContainerSystemUsage.getOrDefault(container.getId(), systemUsage != null ? systemUsage : 0L);

        double cpuUsagePercent = 0.0;
        if (totalUsage != null && systemUsage != null && cpuCount != null &&
                systemUsage > prevSystem && totalUsage > prevTotal && cpuCount > 0) {
            double cpuDelta = totalUsage - prevTotal;
            double systemDelta = systemUsage - prevSystem;
            cpuUsagePercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
        }

        prevContainerTotalUsage.put(container.getId(), totalUsage != null ? totalUsage : 0L);
        prevContainerSystemUsage.put(container.getId(), systemUsage != null ? systemUsage : 0L);

        result.put("cpuUsagePercent", cpuUsagePercent);

        // Memory
        Long memoryUsedBytes = 0L;
        try {
            if (stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null) {
                memoryUsedBytes = stats.getMemoryStats().getUsage();
            }
        } catch (Exception e) {
            memoryUsedBytes = 0L;
        }
        result.put("memoryUsedBytes", memoryUsedBytes);

        // Disk I/O
        long read = 0, write = 0;
        List<com.github.dockerjava.api.model.BlkioStatEntry> ioStats = stats.getBlkioStats().getIoServiceBytesRecursive();
        if (ioStats != null) {
            for (com.github.dockerjava.api.model.BlkioStatEntry entry : ioStats) {
                String op = entry.getOp();
                Long value = entry.getValue();
                if ("Read".equalsIgnoreCase(op)) {
                    read += (value != null ? value : 0);
                } else if ("Write".equalsIgnoreCase(op)) {
                    write += (value != null ? value : 0);
                }
            }
        }
        long prevRead = prevContainerDiskRead.getOrDefault(container.getId(), read);
        long prevWrite = prevContainerDiskWrite.getOrDefault(container.getId(), write);
        result.put("diskReadBytesDelta", read - prevRead);
        result.put("diskWriteBytesDelta", write - prevWrite);
        prevContainerDiskRead.put(container.getId(), read);
        prevContainerDiskWrite.put(container.getId(), write);

        // Network
        Map<String, Object> networkDelta = new LinkedHashMap<>();
        if (stats.getNetworks() != null) {
            Map<String, Long> prevNet = prevContainerNet.getOrDefault(container.getId(), new ConcurrentHashMap<>());
            stats.getNetworks().forEach((iface, net) -> {
                long rx = net.getRxBytes();
                long tx = net.getTxBytes();
                long prevRx = prevNet.getOrDefault(iface + ":rx", rx);
                long prevTx = prevNet.getOrDefault(iface + ":tx", tx);

                Map<String, Object> ifaceData = new LinkedHashMap<>();
                ifaceData.put("rxBytesDelta", rx - prevRx);
                ifaceData.put("txBytesDelta", tx - prevTx);
                networkDelta.put(iface, ifaceData);

                prevNet.put(iface + ":rx", rx);
                prevNet.put(iface + ":tx", tx);
            });
            prevContainerNet.put(container.getId(), prevNet);
        }
        result.put("networkDelta", networkDelta);

        return result;
    }
}
