package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class AlertContainerIdChanged {

    private String messageType = "containerIdChanged";

    private String machineId;    // 바뀐 container id
    private String machineName;  // container name

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 임계값을 넘은 시각
}
