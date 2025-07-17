package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [실시간 임계값 미달(Deceeded) 경보 전송용 DTO]
 * - 특정 호스트/컨테이너의 메트릭이 설정한 임계값보다 '미달' (예: 너무 낮음)일 때
 *   실시간으로 경보를 전송할 때 사용하는 데이터 객체입니다.
 */
@Getter
@Setter
public class AlertThresholdDeceeded {

    // 경보 구분용 고정 메시지 타입 (프론트 분기 처리 등에 활용, 항상 "thresholdDeceeded")
    private String messageType = "thresholdDeceeded";

    private String machineId;    // 경보 발생 대상 호스트/컨테이너의 고유 ID
    private String machineName;  // 경보 발생 대상의 사람이 알아보기 쉬운 이름
    private String metricName;   // 임계 미달이 발생한 메트릭명 (예: temperature, cpu 등)
    private String threshold;    // 비교 기준이 된 임계값 (문자열, 단위 포함 가능)
    private String value;        // 실제 측정된 임계 미달 값 (문자열, 단위 포함 가능)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 임계 미달 감지(경보 발생) 시각

}
