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

    /**
     * 에러 메시지(사유)
     * - 예: "containerIdChanged", "thresholdDeceeded", "thresholdExceeded", "timeout", "zerovalue" 중 하나
     */
    private String error;

    /**
     * 임계값 체크 실패 당시의 overThreshold(또는 underThreshold) 값들
     * - key: 메트릭명(ex: "networkRx", "DiskReadDelta" 등)
     * - value: 해당 메트릭의 임계값(String, 단위/포맷 포함 가능)
     */
    private Map<String, String> thresholdValues;
}
