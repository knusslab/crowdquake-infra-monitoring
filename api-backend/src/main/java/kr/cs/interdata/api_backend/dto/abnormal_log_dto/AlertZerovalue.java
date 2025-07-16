package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class AlertZerovalue {

    private String messageType = "zerovalue";

    private String machineId;    // 메시지를 보낸 호스트/컨테이너의 id
    private String machineName;  // 메시지를 보낸 호스트/컨테이너의 name

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 임계값을 넘은 시각
}
