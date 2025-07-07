package kr.cs.interdata.api_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.api_backend.dto.ThresholdStore;
import kr.cs.interdata.api_backend.infra.MetricWebsocketSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricService {

    private final Logger logger = LoggerFactory.getLogger(MetricService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThresholdService thresholdService;
    private final MetricWebsocketSender metricWebsocketSender;


    public MetricService(ThresholdService thresholdService,
                         ThresholdStore thresholdStore,
                         MetricWebsocketSender metricWebsocketSender) {
        this.thresholdService = thresholdService;
        this.metricWebsocketSender = metricWebsocketSender;
    }

    // consumer -> api-backend
    public Object sendMetric(String metric) {
        JsonNode metricsNode = parseJson(metric);
        metricWebsocketSender.handleMessage(metricsNode);

        // is exceeded threshold
        thresholdService.calcThreshold(metric);

        // logger
        if (metricsNode.has("hostId")) {
            logger.info("Sending threshold metric: {}", metricsNode.get("hostId").asText());
        }
        else if (metricsNode.has("containerId")) {
            logger.info("Sending threshold metric: {}", metricsNode.get("containerId").asText());
        }
        else {
            logger.warn("존재하지 않는 id입니다.");
        }

        return "ok";
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
