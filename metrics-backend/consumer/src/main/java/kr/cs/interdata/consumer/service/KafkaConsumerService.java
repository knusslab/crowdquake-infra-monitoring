package kr.cs.interdata.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.consumer.infra.MetricWebsocketHandler;
import kr.cs.interdata.consumer.infra.MetricWebsocketSender;
import kr.cs.interdata.consumer.infra.ThresholdStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KafkaConsumerService {

    private final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThresholdStore thresholdStore;
    private final ThresholdService thresholdService;
    private final MetricWebsocketSender metricWebsocketSender;

    @Autowired
    public KafkaConsumerService(
            ThresholdStore thresholdStore,
            ThresholdService thresholdService,
            MetricWebsocketSender metricWebsocketSender) {
        this.thresholdStore = thresholdStore;
        this.thresholdService = thresholdService;
        this.metricWebsocketSender = metricWebsocketSender;
    }

    /**
     * 	- Kafka - "host"로부터 배치 메시지를 수신하고 처리하는 메서드
     * 	type : BATCH
     * 	listener Type : BatchMessageListener
     * 	method parameter : onMessage(ConsumerRecords<K, V> data)
     *
     * @param records   지정 토픽에서 받아온 데이터 list
     */
    @KafkaListener(
            topics = "${KAFKA_TOPIC_HOST}",
            groupId = "${KAFKA_GROUP_ID_STORAGE_GROUP}",
            containerFactory = "customContainerFactory"
    )
    public void batchListenerForHost(ConsumerRecords<String, String> records, Acknowledgment ack) {
        List<Mono<Void>> asyncTasks = new ArrayList<>(); // 비동기 작업을 저장할 리스트

        for (ConsumerRecord<String, String> record : records) {
            String json = record.value();

            try {
                JsonNode metricsNode = parseJson(json);

                // *******************************
                //      transmit to websocket
                // *******************************
                // metricWebsocketHandler.sendMetricMessage(json);
                metricWebsocketSender.handleMessage(String.valueOf(metricsNode.get("hostId").asText()),"host",metricsNode);

                // ***********************
                //      set timestamp
                // ***********************
                LocalDateTime violationTime = LocalDateTime.now().withNano(0);

                // ***************************
                //      compare threshold
                // ***************************
                double metricValue = 0.0;
                String metricName = null;
                boolean isNormal;

                // type ID
                String machineId = null;
                if (metricsNode.has("hostId")) {
                    machineId = metricsNode.get("hostId").asText();
                }
                else {
                    logger.warn("Host - 존재하지 않는 id입니다.");
                }

                // CPU, Memory, Disk
                for (int i = 0;i < 3;i++){
                    if (i == 0) {
                        metricName = "cpu";
                        metricValue = metricsNode.has("cpuUsagePercent")
                                ? metricsNode.get("cpuUsagePercent").asDouble()
                                : 0.0;
                    }
                    if (i == 1) {
                        metricName = "memory";
                        metricValue = metricsNode.has("memoryUsedBytes")
                                ? metricsNode.get("memoryUsedBytes").asDouble()
                                : 0.0;
                    }
                    if (i == 2) {
                        metricName = "disk";
                        double diskReadBytesDelta = metricsNode.has("diskReadBytesDelta")
                                ? metricsNode.get("diskReadBytesDelta").asDouble()
                                : 0.0;
                        double diskWriteBytesDelta = metricsNode.has("diskWriteBytesDelta")
                                ? metricsNode.get("diskWriteBytesDelta").asDouble()
                                : 0.0;
                        metricValue = diskReadBytesDelta + diskWriteBytesDelta;
                    }

                    // 각 메트릭별 threshold를 조회해 초과하면 db저장을 위해 api-backend로 데이터 보낸 후, 로깅함.
                    processThreshold("host", machineId,
                            metricName, metricValue, violationTime);
                }

                // Network
                JsonNode networkNode = metricsNode.path("networkDelta");

                metricName = "network";
                if (!networkNode.isMissingNode() && networkNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> interfaces = networkNode.fields();
                    while (interfaces.hasNext()) {
                        Map.Entry<String, JsonNode> entry = interfaces.next();
                        String interfaceName = entry.getKey();
                        JsonNode interfaceData = entry.getValue();

                        double txBytesDelta = interfaceData.has("txBytesDelta")
                                ? interfaceData.get("txBytesDelta").asDouble()
                                : 0.0;
                        double rxBytesDelta = interfaceData.has("rxBytesDelta")
                                ? interfaceData.get("rxBytesDelta").asDouble()
                                : 0.0;
                        metricValue = txBytesDelta + rxBytesDelta;

                        // 각 메트릭별 threshold를 조회해 초과하면 DB에 저장 후, 로깅함.
                        isNormal = processThreshold("host", machineId,
                                metricName, metricValue, violationTime);

                        // 만약 어느 network에서 비정상값이 존재한다면
                        // 비정상적인 네트워크가 존재한다는 것만 알리고 iteration을 중단한다.
                        if (!isNormal)  break;
                    }
                } else {
                    logger.warn("Host:{} - network 데이터를 찾을 수 없습니다.", machineId);
                }

                logger.info("Kafka Record(Host) 처리 성공: {}", record);

            } catch (InvalidJsonException e) {
                logger.error("Host - 잘못된 JSON 형식 - key: {}, value: {}, error: {}", record.key(), record.value(), e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warn("Host - JSON 필드 누락 - key: {}, value: {}, 원인: {}", record.key(), record.value(), e.getMessage());
            } catch (Exception e) {
                logger.error("Host - 예상치 못한 예외 발생 - key: {}, value: {}", record.key(), record.value(), e);
            }
        }
        // 수동 커밋
        ack.acknowledge();
    }

    /**
     * 	- Kafka - "container"로부터 배치 메시지를 수신하고 처리하는 메서드
     * 	type : BATCH
     * 	listener Type : BatchMessageListener
     * 	method parameter : onMessage(ConsumerRecords<K, V> data)
     *
     * @param records   지정 토픽에서 받아온 데이터 list
     */
    @KafkaListener(
            topics = "${KAFKA_TOPIC_CONTAINER}",
            groupId = "${KAFKA_GROUP_ID_STORAGE_GROUP}",
            containerFactory = "customContainerFactory"
    )
    public void batchListenerForContainer(ConsumerRecords<String, String> records, Acknowledgment ack) {
        List<Mono<Void>> asyncTasks = new ArrayList<>(); // 비동기 작업을 저장할 리스트

        for (ConsumerRecord<String, String> record : records) {
            String json = record.value();

            try {
                JsonNode metricsNode = parseJson(json);

                // *******************************
                //      transmit to websocket
                // *******************************
                // metricWebsocketHandler.sendMetricMessage(json);
                metricWebsocketSender.handleMessage(String.valueOf(metricsNode.get("containerId").asText()),"container",metricsNode);

                // ***********************
                //      set timestamp
                // ***********************
                LocalDateTime violationTime = LocalDateTime.now().withNano(0);


                // ***************************
                //      compare threshold
                // ***************************
                double metricValue = 0.0;
                String metricName = null;
                boolean isNormal;

                // type ID
                String machineId = null;
                if (metricsNode.has("containerId")) {
                    machineId = metricsNode.get("containerId").asText();
                }
                else {
                    logger.warn("Container - 존재하지 않는 id입니다.");
                }

                // CPU, Memory, Disk
                for (int i = 0;i < 3;i++){
                    if (i == 0) {
                        metricName = "cpu";
                        metricValue = metricsNode.has("cpuUsagePercent")
                                ? metricsNode.get("cpuUsagePercent").asDouble()
                                : 0.0;
                    }
                    if (i == 1) {
                        metricName = "memory";
                        metricValue = metricsNode.has("memoryUsedBytes")
                                ? metricsNode.get("memoryUsedBytes").asDouble()
                                : 0.0;
                    }
                    if (i == 2) {
                        metricName = "disk";
                        double diskReadBytesDelta = metricsNode.has("diskReadBytesDelta")
                                ? metricsNode.get("diskReadBytesDelta").asDouble()
                                : 0.0;
                        double diskWriteBytesDelta = metricsNode.has("diskWriteBytesDelta")
                                ? metricsNode.get("diskWriteBytesDelta").asDouble()
                                : 0.0;
                        metricValue = diskReadBytesDelta + diskWriteBytesDelta;
                    }

                    // 각 메트릭별 threshold를 조회해 초과하면 db저장을 위해 api-backend로 데이터 보낸 후, 로깅함.
                    processThreshold("container", machineId,
                            metricName, metricValue, violationTime);
                }

                // Network
                JsonNode networkNode = metricsNode.path("networkDelta");

                metricName = "network";
                if (!networkNode.isMissingNode() && networkNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> interfaces = networkNode.fields();
                    while (interfaces.hasNext()) {
                        Map.Entry<String, JsonNode> entry = interfaces.next();
                        String interfaceName = entry.getKey();
                        JsonNode interfaceData = entry.getValue();

                        double txBytesDelta = interfaceData.has("txBytesDelta")
                                ? interfaceData.get("txBytesDelta").asDouble()
                                : 0.0;
                        double rxBytesDelta = interfaceData.has("rxBytesDelta")
                                ? interfaceData.get("rxBytesDelta").asDouble()
                                : 0.0;
                        metricValue = txBytesDelta + rxBytesDelta;

                        // 각 메트릭별 threshold를 조회해 초과하면 DB에 저장 후, 로깅함.
                        isNormal = processThreshold("container", machineId,
                                metricName, metricValue, violationTime);

                        // 만약 어느 network에서 비정상값이 존재한다면
                        // 비정상적인 네트워크가 존재한다는 것만 알리고 iteration을 중단한다.
                        if (!isNormal)  break;
                    }
                } else {
                    logger.warn("Container:{} - network 데이터를 찾을 수 없습니다.", machineId);
                }

                logger.info("Kafka Record(Container) 처리 성공: {}", record);

            } catch (InvalidJsonException e) {
                logger.error("Container - 잘못된 JSON 형식 - key: {}, value: {}, error: {}", record.key(), record.value(), e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warn("Container - JSON 필드 누락 - key: {}, value: {}, 원인: {}", record.key(), record.value(), e.getMessage());
            } catch (Exception e) {
                logger.error("Container - 예상치 못한 예외 발생 - key: {}, value: {}", record.key(), record.value(), e);
            }
        }
        // 수동 커밋
        ack.acknowledge();
    }


    /**
     *  - 각 메트릭별 threshold를 조회한 후, 
     *      초과하면 DB에 저장하는 API를 호출한 다음, 필요하다면 로깅함.
     * 
     * @param type          처리할 데이터의 type ("host" or "container")
     * @param machineId      처리할 데이터의 머신 id
     * @param metricName    threshold를 비교할 메트릭의 이름(ex. "cpu", "disk",...)
     * @param value         threshold와 비교할 메트릭의 모니터링 값
     * @param violationTime 메트릭을 받아온 시각
     */
    public boolean processThreshold(String type, String machineId,
                                 String metricName, Double value, LocalDateTime violationTime) {
        // thresholdStore에서 해당 메트릭의 임계값을 가져옴
        boolean isNormal = false;
        Double threshold = thresholdStore.getThreshold(type, metricName);

        // 임계값을 정상적으로 받아오고 임계값 초과 시 API로 전송
        if (threshold != null && value > threshold) {
            logger.warn("임계값 초과: {} -> {} = {} (임계값: {})", type, metricName, value, threshold);

            // 임계값 초과 데이터를 API 백엔드로 전송
            // API 호출은 대기시간이 있을 수 있으므로, sendThresholdViolation함수 내 호출에서 비동기 작업 처리한다.
            thresholdService.sendThresholdViolation(type, machineId, metricName, value, violationTime);

        }
        // 임계값을 정상적으로 받아오고, 임계값이 초과되지 않음.
        else if (threshold != null && (value < threshold || value.equals(threshold))) {
            isNormal = true;
        }
        // 임계값을 정상적으로 받아오지 못함.
        else {
            logger.warn("임계값이 조회되지 않았습니다.");
        }
        return isNormal;
    }

    // json 파싱
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new InvalidJsonException("JSON 파싱 실패", e);
        }
    }
    
    // 사용자 정의 예외
    public static class InvalidJsonException extends RuntimeException {
        public InvalidJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}