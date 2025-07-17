package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * [메트릭 임계값 설정 전달용 DTO]
 * - host 또는 container 등 특정 타입의 주요 메트릭별 임계값(Threshold)을 한 번에 주고받기 위한 데이터 객체입니다.
 * - 예: typeName("host", "container" 등)에 따라 각각의 threshold 값을 매핑하여 전달합니다.
 */
@Getter
@Setter
public class ThresholdSetting {

    private String cpuPercent;    // CPU 사용량의 임계값 (예: "85" 또는 "90" 등 - % 단위로 표시)
    private String memoryUsage;   // 메모리 사용량 임계값 (예: "20000000000" - bytes 단위 등)
    private String diskReadDelta; // 디스크 읽기 속도 임계값 (예: "40000000" - bytes 등)
    private String diskWriteDelta; // 디스크 쓰기 속도 임계값 (예: "40000000" - bytes 등)
    private String networkRx;     // 네트워크 수신량 임계값 (예: "300000" - bytes 등)
    private String networkTx;     // 네트워크 송신량 임계값 (예: "300000" - bytes 등)
    private String temperature;   // 온도 임계값 (예: "50" - 도씨(°C) 단위 등)
}
