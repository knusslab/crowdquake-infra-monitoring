package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [실시간 임계값 초과 경보 전송용 DTO]
 * - 특정 머신/컨테이너에서 메트릭이 임계값을 초과(혹은 미달)한 경우
 *   실시간 경보를 보내기 위한 데이터 전송 객체입니다.
 */
@Getter
@Setter
public class AlertThresholdExceeded {

    // 메시지 타입 식별자(프론트 분기 처리 등에서 사용, 항상 "thresholdExceeded")
    private String messageType = "thresholdExceeded";

    private String machineId;   // 경보 대상 호스트/컨테이너의 ID
    private String machineName; // 경보 대상 호스트/컨테이너의 name
    private String metricName;  // 초과된 메트릭 이름(cup, memory, ...)
    private String threshold;   // 기준으로 삼았던 임계값 (문자열로 표시, 값과 단위 포함 가능)
    private String value;       // 실제로 감지된 메트릭 값 (임계값 초과 시점의 값)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;    // 임계초과 감지(경보 발생) 시각
}
