package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HostMachineInventory")
@Getter
@Setter
public class HostMachineInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number; // 누적 값

    @ManyToOne
    @JoinColumn(name = "typeId")
    private TargetType type; // host's 타입

    private String hostId; // host id (host별 생성된 머신 id)
    private String hostName; // host name (host별 이름)
}
