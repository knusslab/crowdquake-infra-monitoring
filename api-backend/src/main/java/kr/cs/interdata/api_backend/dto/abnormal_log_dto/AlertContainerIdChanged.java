package kr.cs.interdata.api_backend.dto.abnormal_log_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [컨테이너 ID 변경 경보 전송용 DTO]
 * - 컨테이너의 식별자(ID)가 변경되었을 때 실시간 알림(경보)을 전송하기 위한 데이터 객체입니다.
 */
@Setter
@Getter
public class AlertContainerIdChanged {

    // 알림(경보) 타입 명시 (항상 "containerIdChanged"로 고정, 프론트 이벤트 분기 등에 사용)
    private String messageType = "containerIdChanged";

    private String machineId;    // 변경된(바뀐) 컨테이너의 ID
    private String machineName;  // 해당 컨테이너의 이름(가독성을 위해, 프론트 UI 안내 등에서 활용)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp; // 컨테이너 ID 변경 감지/발생 시각(ISO-8601 문자열로 직렬화)
}
