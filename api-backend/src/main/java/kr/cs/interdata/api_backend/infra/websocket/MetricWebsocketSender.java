package kr.cs.interdata.api_backend.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MetricWebsocketSender {

    // JSON 변환을 위한 ObjectMapper
    private static final Logger logger = LoggerFactory.getLogger(MetricWebsocketSender.class);

    private final MetricWebsocketHandler metricWebsocketHandler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Autowired
    public MetricWebsocketSender(MetricWebsocketHandler metricWebsocketHandler) {
        this.metricWebsocketHandler = metricWebsocketHandler;
    }

    public void handleMessage(Object metricData) {
        if (metricData == null) {
            logger.error("Null parameter detected - metricData: {}", metricData);
            return;
        }

        executorService.submit(() -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String jsonString = objectMapper.writeValueAsString(metricData);
                metricWebsocketHandler.sendMetricMessage(jsonString);
            } catch (Exception e) {
                logger.error("Failed to send metric message for Machine Error: {}", e.getMessage());
            }
        });
    }


}
