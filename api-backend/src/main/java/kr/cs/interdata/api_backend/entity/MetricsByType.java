package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * - table name : MetricsByType
 *  - 용도 : 타입별 수집할 메트릭 정의 (단위, 임계값 포함)
 *  - 사용 시나리오 :
 *      타입별 수집할 메트릭들의 정보가 저장된다.
 *      메트릭의 정보에는 단위와 임계값이 포함된다.
 *      임계값은 사용자 지정으로 변경 가능한 값이다.
 *
 *  - PK : number   // table에 들어온 순서대로의 누적 번호값
 *  - 테이블 관계 :
 *      TargetType (1) → (N) MetricsByType
 */
@Entity
@Table(name = "MetricsByType")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsByType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number; // 누적 값

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type; // machine's 타입

    private String metricName; // 메트릭 이름
    private String unit; // 단위
    private Double overThresholdValue; // 기준 초과 임계값
    private Double underThresholdValue; // 기준 미달 임계값
}
