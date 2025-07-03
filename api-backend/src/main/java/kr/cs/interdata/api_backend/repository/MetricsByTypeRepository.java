package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.MetricsByType;
import kr.cs.interdata.api_backend.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricsByTypeRepository extends JpaRepository<MetricsByType, Integer> {

    /**
     *  - 주어진 타입 이름(typeName)에 해당하는 MetricsByType 엔티티 목록을 조회한다.
     *
     * @param typeName 조회할 타입 이름 (예: "host")
     * @return 해당 타입의 MetricsByType 리스트
     */
    List<MetricsByType> findByType_Type(String typeName);

    /**
     *  - 주어진 메트릭 이름(metricName)에 해당하는 MetricsByType 엔티티 목록을 조회한다.
     *
     * @param metricName 조회할 메트릭 이름
     * @return 해당 메트릭 이름의 MetricsByType 리스트
     */
    List<MetricsByType> findByMetricName(String metricName);

    /**
     *  - 주어진 타입(type)과 메트릭 이름(metricName)에 해당하는 엔티티가 존재하는지 여부를 반환합니다.
     *
     * @param type          조회할 타입
     * @param metricName    조회할 메트릭 이름
     * @return 해당 조건에 맞는 엔티티가 존재하면 true, 없으면 false
     */
    boolean existsByTypeAndMetricName(TargetType type, String metricName);
}
