package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * - table name : MonitoredMachineInventory
 *  - 용도 : 실제 모니터링 대상 장비 (type + 고유 ID)
 *  - 사용 시나리오 :
 *      모니터링할 머신의 목록을 저장한다.
 *
 *  - PK : id   // 모니터링할 머신에 고유 id를 부여함. (타입+숫자 형식 ex. host001, container233)
 *  - 테이블 관계 :
 *      TargetType (1) → (N) MetricsByType
 */
@Entity
@Table(name = "MonitoredMachineInventory")
@Getter
@Setter
public class MonitoredMachineInventory {

    @Id
    private String id; // machine unique id (타입+숫자)

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type; // machine's 타입

    private String machineId; // machine id (host와 container별 생성된 머신 id)

}
