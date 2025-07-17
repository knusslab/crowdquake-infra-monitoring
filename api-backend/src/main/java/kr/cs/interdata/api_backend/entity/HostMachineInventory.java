package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * [HostMachineInventory 엔티티]
 * - 각 호스트(물리/가상 머신)의 인벤토리(메타 정보)를 저장하는 엔티티 클래스입니다.
 * - JPA를 통해 DB의 "HostMachineInventory" 테이블과 매핑됩니다.
 */
@Entity
@Table(name = "HostMachineInventory")
@Getter
@Setter
public class HostMachineInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number;     // 기본키(자동 증가, 시스템 내부 누적 값, 고유 식별자)

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type;    // 호스트의 타입 정보 (host/container 분류 등, TargetType 테이블의 FK)

    private String hostId;      // 호스트의 고유 머신 ID (서비스 내부적으로 부여한 host별 식별자)
    private String hostName;    // 호스트의 이름 (식별/표시용)
}
