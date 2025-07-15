package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThresholdSetting {

    private String cpuPercent;  // cpu의 threshold
    private String memoryUsage;   // memory의 threshold
    private String diskIO;     // disk의 threshold
    private String networkTraffic;  // network의 threshold
    private String temperature; // temperature의 threshold
}
