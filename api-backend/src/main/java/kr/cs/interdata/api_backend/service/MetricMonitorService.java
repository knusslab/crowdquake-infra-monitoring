package kr.cs.interdata.api_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import kr.cs.interdata.api_backend.infra.cache.MachineMetricTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;

@Service
public class MetricMonitorService {

    private final Cache<String, MachineMetricTimestamp> metricTimestampCache;
    private final ThresholdService thresholdService;
    private final Logger logger = LoggerFactory.getLogger(MetricMonitorService.class);

    @Autowired
    public MetricMonitorService(
            Cache<String, MachineMetricTimestamp> metricTimestampCache,
            ThresholdService thresholdService) {
        this.metricTimestampCache = metricTimestampCache;
        this.thresholdService = thresholdService;
    }

    // 메트릭 수신 시 호출: 캐시에 시간 저장
    public void updateMetricTimestamp(String type, String machineId, String machineName) {
        String key = type + ":" + machineId;
        metricTimestampCache.put(key, new MachineMetricTimestamp(LocalDateTime.now(), machineName));
    }

    @Async
    public void updateTimestamps(JsonNode metricsNode) {
        String type = metricsNode.get("type").asText(); // "host"
        String hostId = metricsNode.get("hostId").asText();
        String hostName = metricsNode.get("name").asText(); // 여기에 있음!

        // 1. 호스트
        updateMetricTimestamp(type, hostId, hostName);

        // 2. 컨테이너
        JsonNode containersNode = metricsNode.get("containers");
        if (containersNode != null && containersNode.isObject()) {
            Iterator<String> fieldNames = containersNode.fieldNames();
            while (fieldNames.hasNext()) {
                String containerId = fieldNames.next();
                JsonNode containerNode = containersNode.get(containerId);
                String containerName = containerNode.get("name").asText(); // 여기서 name 있음
                updateMetricTimestamp("container", containerId, containerName);
            }
        }
    }

    // 감시용 스케줄러
    @Scheduled(fixedRate = 30_000)
    public void checkMetricTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, MachineMetricTimestamp> snapshot = metricTimestampCache.asMap();

        for (Map.Entry<String, MachineMetricTimestamp> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            MachineMetricTimestamp data = entry.getValue();

            if (Duration.between(data.getTimestamp(), now).toSeconds() >= 60) {
                String[] parts = key.split(":");
                String type = parts[0];
                String machineId = parts[1];

                thresholdService.storeTimeout(type, machineId, data.getMachineName(), now);

                logger.warn("store timeout for machine {} for machine {}", machineId, type);
            }
        }
    }


}
