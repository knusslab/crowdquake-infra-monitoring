package kr.cs.interdata.producer.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Gson gson = new Gson();
    private final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    @Value("${KAFKA_TOPIC_HOST}")
    private String topic_host;

    @Value("${KAFKA_TOPIC_CONTAINER}")
    private String topic_container;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void routeMessageBasedOnType(String jsonPayload) {
        JsonObject json = gson.fromJson(jsonPayload, JsonObject.class);
        String type = json.get("type").getAsString();

        String topic;
        if ("host".equalsIgnoreCase(type)) {
            topic = topic_host;
        } else if ("container".equalsIgnoreCase(type)) {
            topic = topic_container;
        } else {
            topic = null;
            logger.error("Unknown topic type: {}", type);
        }

        kafkaTemplate.send(topic, jsonPayload);
        kafkaTemplate.flush();
    }
}