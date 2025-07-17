package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [실시간 Timeout 경보 전송용 DTO]
 * - 특정 머신/컨테이너에서 1분 이상 데이터 미수신 시(즉, 사라지거나 장애, 삭제 등) 경보를 보내는 객체.
 */
@Setter
@Getter
public class AlertTimeout {

    private String messageType = "timeout"; // 메시지 타입 식별자 ("timeout")

    private String machineId;    // 경보 대상 호스트/컨테이너의 ID
    private String machineName;  // 경보 대상 호스트/컨테이너의 이름

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;  // 타임아웃 발생(감지) 시각
}
