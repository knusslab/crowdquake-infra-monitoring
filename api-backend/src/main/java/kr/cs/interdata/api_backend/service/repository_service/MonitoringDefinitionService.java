package kr.cs.interdata.api_backend.service.repository_service;

import kr.cs.interdata.api_backend.dto.ThresholdSetting;
import kr.cs.interdata.api_backend.entity.MetricsByType;
import kr.cs.interdata.api_backend.entity.TargetType;
import kr.cs.interdata.api_backend.repository.MetricsByTypeRepository;
import kr.cs.interdata.api_backend.repository.TargetTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MonitoringDefinitionService {

    private final Logger logger = LoggerFactory.getLogger(MonitoringDefinitionService.class);
    private final TargetTypeRepository targetTypeRepository;
    private final MetricsByTypeRepository metricsByTypeRepository;

    @Autowired
    public MonitoringDefinitionService(
            TargetTypeRepository targetTypeRepository,
            MetricsByTypeRepository metricsByTypeRepository) {
        this.targetTypeRepository = targetTypeRepository;
        this.metricsByTypeRepository = metricsByTypeRepository;
    }

    // 타입 등록 - targetType
    public void registerType(String type) {
        TargetType targetType = new TargetType();
        targetType.setType(type);
        targetTypeRepository.save(targetType);

        logger.info("Register type {} successfully.", type);
    }

    // 새로운 메트릭 등록
    public void saveMetric(String metricName, String unit, Double threshold){
        MetricsByType metric = new MetricsByType();
        metric.setMetricName(metricName);
        metric.setUnit(unit);
        metric.setThresholdValue(threshold);
        metricsByTypeRepository.save(metric);

        logger.info("Save new metric : {}({})", metricName, unit);
    }

    /**
     *  - 특정 타입의 메트릭 임계값을 ThresholdSetting으로 매핑하여 조회
     *
     * @param typeName "host" 또는 "container"와 같은 타입명
     * @return ThresholdSetting 객체
     */
    public ThresholdSetting findThresholdByType(String typeName) {
        List<MetricsByType> metrics = metricsByTypeRepository.findByType_Type(typeName);

        ThresholdSetting thresholdSetting = new ThresholdSetting();
        metrics.forEach(metric -> {
            switch (metric.getMetricName()) {
                case "cpu":
                    thresholdSetting.setCpuPercent(metric.getThresholdValue().toString());
                    break;
                case "memory":
                    thresholdSetting.setMemoryPercent(metric.getThresholdValue().toString());
                    break;
                case "disk":
                    thresholdSetting.setDiskPercent(metric.getThresholdValue().toString());
                    break;
                case "network":
                    thresholdSetting.setNetworkTraffic(metric.getThresholdValue().toString());
                    break;
                default:
                    logger.warn("Unknown metric name found: {}", metric.getMetricName());
            }
        });

        return thresholdSetting;
    }

    /**
     * - 특정 메트릭의 임계값 업데이트
     *
     * @param metricName 메트릭 이름 (cpu, memory, disk, network)
     * @param thresholdValue 업데이트할 임계값
     */
    public void updateThresholdByMetricName(String metricName, double thresholdValue) {
        // 해당 메트릭 이름을 가진 모든 MetricsByType 조회
        List<MetricsByType> metrics = metricsByTypeRepository.findByMetricName(metricName);

        // 각각의 임계값 업데이트
        metrics.forEach(metric -> metric.setThresholdValue(thresholdValue));

        // 일괄 저장
        metricsByTypeRepository.saveAll(metrics);

        logger.info("Updated threshold for {} metrics to {}", metricName, thresholdValue);
    }

    /**
     *  - 모든 타입의 모든 메트릭 임계값을 조회하여 Map 형태로 반환
     *
     * @return Map<type(String), Map<metric_name(String), threshold(Double)>> resultMap
     */
    public Map<String, Map<String, Double>> findAllThresholdsGroupedByType() {
        // Key는 'host' 또는 'container', Value는 해당 타입에 대한 메트릭들에 대한 Map을 선언한다.
        Map<String, Map<String, Double>> resultMap = new ConcurrentHashMap<>();

        // 모든 MetricsByType 데이터를 가져옴
        List<MetricsByType> metricsList = metricsByTypeRepository.findAll();

        // 데이터를 순차적으로 처리하여 결과 Map에 추가한다.
        metricsList.forEach(metric -> {
            String typeKey = metric.getType().getType(); // type (host, container)
            Map<String, Double> metricMap = resultMap.computeIfAbsent(typeKey, k -> new ConcurrentHashMap<>());
            metricMap.put(metric.getMetricName(), metric.getThresholdValue());
        });

        return resultMap;
    }

}
