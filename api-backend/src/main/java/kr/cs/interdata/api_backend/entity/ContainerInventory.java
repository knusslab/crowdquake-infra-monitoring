package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "ContainerInventory",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"hostName", "containerName"})
        }
)
@Getter
@Setter
public class ContainerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number; // 누적 값

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type; // machine's 타입

    private String hostName;

    private String containerId; // container id (container별 생성된 머신 id)
    private String containerName; // container name (container별 이름)
}
