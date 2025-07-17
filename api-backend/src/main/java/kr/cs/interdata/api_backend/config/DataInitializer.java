package kr.cs.interdata.api_backend.config;

import kr.cs.interdata.api_backend.infra.ThresholdStore;
import kr.cs.interdata.api_backend.entity.MetricsByType;
import kr.cs.interdata.api_backend.entity.TargetType;
import kr.cs.interdata.api_backend.repository.MetricsByTypeRepository;
import kr.cs.interdata.api_backend.repository.TargetTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final TargetTypeRepository targetTypeRepository;
    private final MetricsByTypeRepository metricsByTypeRepository;
    private final ThresholdStore thresholdStore;

    @Autowired
    public DataInitializer(TargetTypeRepository targetTypeRepository,
                           MetricsByTypeRepository metricsByTypeRepository,
                           ThresholdStore thresholdStore) {
        this.targetTypeRepository = targetTypeRepository;
        this.metricsByTypeRepository = metricsByTypeRepository;
        this.thresholdStore = thresholdStore;
    }

    @Override
    public void run(String... args) {
        // ===== 1. host, container 타입이 DB에 존재하는지 확인, 없으면 새로 저장 =====
        TargetType hostType = targetTypeRepository.findByType("host")
                .orElseGet(() -> targetTypeRepository.save(TargetType.builder().type("host").build()));

        TargetType containerType = targetTypeRepository.findByType("container")
                .orElseGet(() -> targetTypeRepository.save(TargetType.builder().type("container").build()));

        // ===== 2. 각 타입별 메트릭이 이미 저장되어 있는지 확인, 없으면 새로 insert (중복 방지) =====
        // host type용 주요 메트릭 등록
        insertMetricIfNotExists(hostType, "cpu", "%", 85.0, 0.0);
        insertMetricIfNotExists(hostType, "memory", "bytes", 20000000000.0, 0.0);
        insertMetricIfNotExists(hostType, "diskReadDelta", "bytes", 40000000.0, 0.0);
        insertMetricIfNotExists(hostType, "diskWriteDelta", "bytes", 40000000.0, 0.0);
        insertMetricIfNotExists(hostType, "networkRx", "bytes", 300000.0, 0.0);
        insertMetricIfNotExists(hostType, "networkTx", "bytes", 300000.0, 0.0);
        insertMetricIfNotExists(hostType, "temperature", "°C", 50.0, 5.0);

        // container type용 주요 메트릭 등록
        insertMetricIfNotExists(containerType, "cpu", "%", 85.0, 0.0);
        insertMetricIfNotExists(containerType, "memory", "bytes", 20000000000.0, 0.0);
        insertMetricIfNotExists(containerType, "diskReadDelta", "bytes", 40000000.0, 0.0);
        insertMetricIfNotExists(containerType, "diskWriteDelta", "bytes", 40000000.0, 0.0);
        insertMetricIfNotExists(containerType, "networkRx", "bytes", 300000.0, 0.0);
        insertMetricIfNotExists(containerType, "networkTx", "bytes", 300000.0, 0.0);

        // ===== 3. 임계값(over) 등록: 각 메트릭별 한계값(초과 시 위험)을 ThresholdStore에 저장 =====
        // host type - over threshold 값 등록
        thresholdStore.updateOverThreshold("host", "cpu", 85.0);
        thresholdStore.updateOverThreshold("host", "memory", 20000000000.0);
        thresholdStore.updateOverThreshold("host", "diskReadDelta", 40000000.0);
        thresholdStore.updateOverThreshold("host", "diskWriteDelta", 40000000.0);
        thresholdStore.updateOverThreshold("host", "networkRx", 300000.0);
        thresholdStore.updateOverThreshold("host", "networkTx", 300000.0);
        thresholdStore.updateOverThreshold("host", "temperature", 50.0);

        // container type - over threshold 값 등록
        thresholdStore.updateOverThreshold("container", "cpu", 85.0);
        thresholdStore.updateOverThreshold("container", "memory", 20000000000.0);
        thresholdStore.updateOverThreshold("container", "diskReadDelta", 40000000.0);
        thresholdStore.updateOverThreshold("container", "diskWriteDelta", 40000000.0);
        thresholdStore.updateOverThreshold("container", "networkRx", 300000.0);
        thresholdStore.updateOverThreshold("container", "networkTx", 300000.0);

        // ===== 4. 임계값(under) 등록: 각 메트릭별 한계값(이하 시 위험)을 ThresholdStore에 저장 =====
        // host type - under threshold 값 등록
        thresholdStore.updateUnderThreshold("host", "cpu", 0.0);
        thresholdStore.updateUnderThreshold("host", "memory", 0.0);
        thresholdStore.updateUnderThreshold("host", "diskReadDelta", 0.0);
        thresholdStore.updateUnderThreshold("host", "diskWriteDelta", 0.0);
        thresholdStore.updateUnderThreshold("host", "networkRx", 0.0);
        thresholdStore.updateUnderThreshold("host", "networkTx", 0.0);
        thresholdStore.updateUnderThreshold("host", "temperature", 5.0);

        // container type - under threshold 값 등록
        thresholdStore.updateUnderThreshold("container", "cpu", 0.0);
        thresholdStore.updateUnderThreshold("container", "memory", 0.0);
        thresholdStore.updateUnderThreshold("container", "diskReadDelta", 0.0);
        thresholdStore.updateUnderThreshold("container", "diskWriteDelta", 0.0);
        thresholdStore.updateUnderThreshold("container", "networkRx", 0.0);
        thresholdStore.updateUnderThreshold("container", "networkTx", 0.0);

    }

    /**
     *  - type+name인 정보가 MetricsByType 테이블에 존재하지 않는다면 초기 정보를 넣는 메서드
     *
     * @param type          machine's type
     * @param name          metric's name
     * @param unit          Unit of metrics to collect
     * @param overThreshold     metric's over threshold
     * @param underThreshold     metric's under threshold
     */
    private void insertMetricIfNotExists(TargetType type, String name, String unit, Double overThreshold, Double underThreshold) {
        boolean exists = metricsByTypeRepository.existsByTypeAndMetricName(type, name);
        if (!exists) {
            MetricsByType metric = MetricsByType.builder()
                    .type(type)
                    .metricName(name)
                    .unit(unit)
                    .overThresholdValue(overThreshold)
                    .underThresholdValue(underThreshold)
                    .build();
            metricsByTypeRepository.save(metric);
        }
    }

}
