package kr.cs.interdata.api_backend.infra.cache;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 캐시에 저장할 값
@Getter
@Setter
public class MachineMetricTimestamp {
    private final LocalDateTime timestamp;
    private final String machineName; // 고정값

    public MachineMetricTimestamp(LocalDateTime timestamp, String machineName) {
        this.timestamp = timestamp;
        this.machineName = machineName;
    }

}

