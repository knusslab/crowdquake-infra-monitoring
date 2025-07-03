package kr.cs.interdata.consumer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ThresholdRequest {
    private String type;
    private String machineId;
    private String metricName;
    private Double value;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 임계값을 넘은 시각

    public ThresholdRequest(String type, String machineId, String metricName, Double value, LocalDateTime timestamp) {
        this.type = type;
        this.machineId = machineId;
        this.metricName = metricName;
        this.value = value;
        this.timestamp = timestamp;
    }


}
