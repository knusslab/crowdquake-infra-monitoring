package kr.cs.interdata.consumer.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class MetricWebsocketSender {

    // JSON 변환을 위한 ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(MetricWebsocketSender.class);

    private final WebClient webClient;
    private final MetricWebsocketHandler metricWebsocketHandler;

    @Autowired
    public MetricWebsocketSender(WebClient webClient, MetricWebsocketHandler metricWebsocketHandler) {
        this.webClient = webClient;
        this.metricWebsocketHandler = metricWebsocketHandler;
    }

    public void handleMessage(String machineId, String type, Object metricData) {
        if (machineId == null || type == null || metricData == null) {
            logger.error("Null parameter detected - machineId: {}, type: {}, metricData: {}", machineId, type, metricData);
            return;
        }

        String requestUrl = "/api/inventory/" + machineId + "/" + type;

        webClient
                .get()
                .uri(requestUrl)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> logger.error("Failed to call API: {}", e.getMessage()))
                .flatMap(targetId -> {
                    if (targetId == null || targetId.isEmpty()) {
                        logger.warn("Unique ID not found for Machine ID: {}", machineId);
                        return Mono.empty();
                    }

                    try {
                        Map<String, Object> dataMap = objectMapper.convertValue(
                                metricData,
                                new TypeReference<Map<String, Object>>() {}
                        );

                        if (dataMap.containsKey("hostId")) {
                            dataMap.put("hostId", targetId);
                        } else if (dataMap.containsKey("containerId")) {
                            dataMap.put("containerId", targetId);
                        } else {
                            logger.warn("hostId/containerId not found in the metric data");
                        }

                        String jsonMessage = objectMapper.writeValueAsString(dataMap);

                        // WebSocket 전송을 Mono로 래핑
                        //return Mono.fromRunnable(() -> metricWebsocketHandler.sendMetricMessage(jsonMessage));

                        // Mono.just로 dataMap emit, doOnNext로 WebSocket 전송
                        return Mono.just(dataMap)
                                .doOnNext(map -> metricWebsocketHandler.sendMetricMessage(jsonMessage));

                    } catch (Exception e) {
                        logger.error("Failed to process message for Machine ID: {}, Type: {}. Error: {}", machineId, type, e.getMessage());
                        return Mono.error(e);
                    }
                })
                .subscribe();
    }


}
