package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThresholdSetting {

    private String cpuPercent;  // cpu의 threshold
    private String memoryUsage;   // memory의 threshold
    private String diskReadDelta;     // disk의 threshold
    private String diskWriteDelta;     // disk의 threshold
    private String networkRx;  // network의 threshold
    private String networkTx;  // network의 threshold
    private String temperature; // temperature의 threshold
}
