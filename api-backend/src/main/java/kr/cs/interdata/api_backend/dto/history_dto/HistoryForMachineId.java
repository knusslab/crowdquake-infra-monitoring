package kr.cs.interdata.api_backend.dto.history_dto;

import lombok.Getter;
import lombok.Setter;

/**
 * [임계값 이력 조회 요청용 DTO]
 * - 특정 머신(호스트/컨테이너 등)의 임계값 변경 이력을 조회할 때
 *   요청 본문에 넘겨주는 데이터 객체입니다.
 */
@Getter
@Setter
public class HistoryForMachineId {

    private String targetId;    // 임계 초과(혹은 변경) 로그 이력을 조회하고자 하는 대상의 식별자(ID)
}

