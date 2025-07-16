package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AlertThresholdDeceeded {

    private String messageType = "thresholdDeceeded";

    private String machineId;    // 메시지를 보낸 호스트/컨테이너의 id
    private String machineName; // 메세지를 보낸 호스트/컨테이너의 name
    private String metricName;  // 메트릭 이름
    private String threshold;   // 임계값 미달 당시의 기준임계값
    private String value;   // 임계값 미달 값

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 임계값 미달된 시각

}
