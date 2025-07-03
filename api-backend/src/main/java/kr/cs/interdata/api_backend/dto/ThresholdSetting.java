package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThresholdSetting {

    private String cpuPercent;  // cpu의 threshold
    private String memoryPercent;   // memory의 threshold
    private String diskPercent;     // disk의 threshold
    private String networkTraffic;  // network의 threshold

}
