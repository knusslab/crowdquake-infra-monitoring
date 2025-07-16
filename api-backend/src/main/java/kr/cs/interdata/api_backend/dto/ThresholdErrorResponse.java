package kr.cs.interdata.api_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ThresholdErrorResponse {
    private String error;
    private Map<String, String> overThresholdValues;
}
