package kr.cs.interdata.api_backend.infra;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  머신 타입당 메트릭이름당 threshold를 저장하는 class
 *  (ex) thresholdMap의 구성
 *      [0] host -> [0] cpuUsage -> cpuUsage's threshold
 *               -> [1] memoryUsage -> memoryUsage's threshold
 *               -> [2] diskIO -> diskIO's threshold
 *      [1] container -> [0] cpuUsage -> cpuUsage's threshold
 *                    -> [1] memoryUsage -> memoryUsage's threshold
 *      ...
 */
@Component
public class ThresholdStore {

    // 머신 타입(host/container) → 메트릭 이름 → 임계값 을 저장하는 맵
    private final Map<String, Map<String, Double>> overThresholdMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> underThresholdMap = new ConcurrentHashMap<>();

    /**
     * 특정 타입(type)에 대해 메트릭(metric)의 임계값(value)을 갱신한다.
     * 해당 type이 처음 추가되는 경우, 내부적으로 새로운 Map을 생성한다.
     * 예시 : thresholdStore.updateThreshold("host", "cpu", 80.0);
     *
     * @param type   머신 종류 (예: "host" 또는 "container")
     * @param metric 메트릭 이름 (예: "cpu", "memory" 등)
     * @param value  임계값 (예: 80.0)
     */
    public void updateOverThreshold(String type, String metric, Double value) {
        overThresholdMap.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .put(metric, value);
    }

    public void updateUnderThreshold(String type, String metric, Double value) {
        underThresholdMap.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .put(metric, value);
    }

    /**
     * 특정 타입(type)과 메트릭(metric)에 해당하는 임계값을 반환한다.
     * 값이 없으면 null을 반환한다.
     * 예시 : thresholdStore.getThreshold("host", "cpu"); // → 80.0
     *
     * @param type   머신 종류
     * @param metric 메트릭 이름
     * @return 임계값 또는 null
     */
    public Double getOverThreshold(String type, String metric) {
        return Optional.ofNullable(overThresholdMap.get(type))
                .map(m -> m.get(metric))
                .orElse(null);
    }

    public Double getUnderThreshold(String type, String metric) {
        return Optional.ofNullable(underThresholdMap.get(type))
                .map(m -> m.get(metric))
                .orElse(null);
    }

    public Map<String, Double> getOverThresholdValues() {
        Map<String, Double> thresholds = new LinkedHashMap<>();

        thresholds.put("cpuPercent", getOverThreshold("host", "cpu"));
        thresholds.put("memoryUsage", getOverThreshold("host", "memory"));
        thresholds.put("diskReadDelta", getOverThreshold("host", "diskReadDelta"));
        thresholds.put("diskWriteDelta", getOverThreshold("host", "diskWriteDelta"));
        thresholds.put("networkRx", getOverThreshold("host", "networkRx"));
        thresholds.put("networkTx", getOverThreshold("host", "networkTx"));
        thresholds.put("temperature", getOverThreshold("host", "temperature"));

        return thresholds;
    }

    public Map<String, Double> getUnderThresholdValues() {
        Map<String, Double> thresholds = new LinkedHashMap<>();

        thresholds.put("cpuPercent", getUnderThreshold("host", "cpu"));
        thresholds.put("memoryUsage", getUnderThreshold("host", "memory"));
        thresholds.put("diskReadDelta", getUnderThreshold("host", "diskReadDelta"));
        thresholds.put("diskWriteDelta", getUnderThreshold("host", "diskWriteDelta"));
        thresholds.put("networkRx", getUnderThreshold("host", "networkRx"));
        thresholds.put("networkTx", getUnderThreshold("host", "networkTx"));
        thresholds.put("temperature", getUnderThreshold("host", "temperature"));

        return thresholds;
    }

}
