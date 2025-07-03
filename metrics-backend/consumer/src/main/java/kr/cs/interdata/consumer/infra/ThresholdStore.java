package kr.cs.interdata.consumer.infra;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  머신 타입당 메트릭이름당 threshold를 저장하는 class
 *  ex) thresholdMap의 구성
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
    private final Map<String, Map<String, Double>> thresholdMap = new ConcurrentHashMap<>();

    /**
     * 특정 타입(type)에 대해 메트릭(metric)의 임계값(value)을 갱신한다.
     * 해당 type이 처음 추가되는 경우, 내부적으로 새로운 Map을 생성한다.
     * 예시 : thresholdStore.updateThreshold("host", "cpu", 80.0);
     *
     * @param type   머신 종류 (예: "host" 또는 "container")
     * @param metric 메트릭 이름 (예: "cpu", "memory" 등)
     * @param value  임계값 (예: 80.0)
     */
    public void updateThreshold(String type, String metric, Double value) {
        thresholdMap.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
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
    public Double getThreshold(String type, String metric) {
        return Optional.ofNullable(thresholdMap.get(type))
                .map(m -> m.get(metric))
                .orElse(null);
    }

}