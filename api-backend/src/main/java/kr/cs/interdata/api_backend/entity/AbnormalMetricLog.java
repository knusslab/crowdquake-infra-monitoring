package kr.cs.interdata.api_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 *  - table name : AbnormalMetricLog
 *  - 용도 : 과거 이상 기록 전체 저장(이력용)
 *  - 사용 시나리오 :
 *      1. 이상이 생긴 machine-metric 구성의 entity를 이상이 생긴 시각과 더불어 저장한다.
 *      2. machine-metric 구성의 동일 entity가 다른 시각에 이상이 생길 경우 "중복 저장" 가능하다.
 *
 *  - PK : number   // table에 들어온 순서대로의 누적 번호값
 */
@Entity
@Table(name = "AbnormalMetricLog")
@Getter
@Setter
public class AbnormalMetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer number;     // 누적 값

    private String messageType; // anomaly type

    private String machineType; // anomaly machine's type
    private String machineId;   // anomaly machine's id
    private String machineName; // anomaly machine's name

    private String metricName;  // anomaly metric's name
    private Double threshold;   // anomaly가 생긴 당시의 threshold
    private Double value;       // outlier

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;    // anomaly가 생긴 시각
}
