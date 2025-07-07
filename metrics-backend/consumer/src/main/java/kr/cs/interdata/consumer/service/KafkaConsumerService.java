package kr.cs.interdata.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KafkaConsumerService {

    private final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MetricService metricService;

    @Autowired
    public KafkaConsumerService(MetricService metricService) {
        this.metricService = metricService;
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
                //     transmit to API-server
                // *******************************
                metricService.sendThresholdViolation(json);

                logger.info("Kafka Record(Host) 처리 성공: {}", metricsNode.get("hostId").asText());

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
        //List<Mono<Void>> asyncTasks = new ArrayList<>(); // 비동기 작업을 저장할 리스트

        for (ConsumerRecord<String, String> record : records) {
            String json = record.value();

            try {
                JsonNode metricsNode = parseJson(json);

                // *******************************
                //     transmit to API-server
                // *******************************
                metricService.sendThresholdViolation(json);

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