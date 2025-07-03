package kr.cs.interdata.producer;

import kr.cs.interdata.producer.service.KafkaProducerService;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"monitoring.host.dev.json"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class ProducerApplicationTests {

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Test
    @DisplayName("Send mock JSON to Kafka and create topics automatically")
    void testSendMessage() {

        // given
        String jsonPayload = """
            {
              "type": "host",
              "hostId": "1234-5678",
              "cpuUsagePercent": 55.0,
              "memoryTotalBytes": 8192,
              "memoryUsedBytes": 4096,
              "diskTotalBytes": 100000,
              "diskUsedBytes": 50000
            }
        """;

        // when
        kafkaProducerService.routeMessageBasedOnType(jsonPayload);

        // then
    }
}