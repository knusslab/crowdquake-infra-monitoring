package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * [ContainerInventory 엔티티]
 * - 각 컨테이너 인벤토리(메타 정보)를 저장하는 테이블 엔티티 클래스입니다.
 * - JPA를 통해 DB의 "ContainerInventory" 테이블과 매핑되어 사용됩니다.
 */
@Entity
@Table(
        name = "ContainerInventory",
        uniqueConstraints = {
                // 같은 hostName 내에서 containerName이 중복될 수 없도록 유니크 제약
                @UniqueConstraint(columnNames = {"hostName", "containerName"})
        }
)
@Getter
@Setter
public class ContainerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number; // 기본 키(자동 증가), 시스템 내부 누적 값(고유식별용)

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type;    // 이 컨테이너의 타입 정보(TARGET_TYPE 테이블과의 FK)

    private String hostName;    // 이 컨테이너가 소속된 호스트의 이름

    private String containerId; // 컨테이너의 고유 생성 ID(Docker 등에서 부여한 ID)
    private String containerName; // 컨테이너의 식별 이름(운영자가 부여한 이미지 이름 등)
}
