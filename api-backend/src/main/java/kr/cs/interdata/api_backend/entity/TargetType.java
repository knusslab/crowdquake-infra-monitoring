package kr.cs.interdata.api_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 *  - table name : TargetType
 *  - 용도 : Host, Container 등 대상 타입 정의 테이블
 *  - 사용 시나리오 :
 *      type에는 machine의 타입이 저장된다. ex. host, container
 *
 *  - PK : number // table에 들어온 순서대로의 누적 번호값
 *  - 테이블 관계 :
 *      TargetType (1) → (N) MonitoredMachineInventory
 *      TargetType (1) → (N) MetricsByType
 */
@Entity
@Table(name = "TargetType")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number; // 누적 값

    private String type; // machine 타입 -> "host" or "container"

    @OneToMany(mappedBy = "type")
    private List<HostMachineInventory> machines;

    @OneToMany(mappedBy = "type")
    private List<ContainerInventory> containers;

    @OneToMany(mappedBy = "type")
    private List<MetricsByType> metrics;
}
