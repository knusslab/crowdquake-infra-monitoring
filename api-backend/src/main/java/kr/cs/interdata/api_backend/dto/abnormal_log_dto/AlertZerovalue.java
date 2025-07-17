package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 컨테이너가 0 값을 리포팅하거나(주로 꺼져있을 때), 실시간 경보 전송을 위한 DTO입니다.
 */
@Setter
@Getter
public class AlertZerovalue {

    private String messageType = "zerovalue";   // 메시지 타임(항상 "zerovalue")

    private String machineId;    // 경보 대상 호스트/컨테이너의 ID
    private String machineName;  // 경보 대상 호스트/컨테이너의 name

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;    // 이벤트 발생(감지) 시간
}
