package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MachineIdforHistory {

    private String targetId;    // 임계 초과 로그를 찾을 target_ID
}

