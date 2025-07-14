package kr.cs.interdata.api_backend.infra.cache;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 캐시에 저장할 값
@Getter
@Setter
public class MachineMetricTimestamp {
    private final LocalDateTime timestamp;
    private final String machineId; // 컨테이너 ID or 호스트 ID
    private final String machineName;
    private final String parentHostName;// 호스트면 null, 컨테이너면 부모 호스트명

    public MachineMetricTimestamp(LocalDateTime timestamp, String machineId, String machineName, String parentHostName) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.machineName = machineName;
        this.parentHostName = parentHostName;
    }

}

