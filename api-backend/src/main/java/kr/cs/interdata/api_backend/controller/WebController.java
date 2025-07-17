package kr.cs.interdata.api_backend.controller;

import kr.cs.interdata.api_backend.service.MetricService;
import kr.cs.interdata.api_backend.service.repository_service.MachineInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.cs.interdata.api_backend.dto.*;
import kr.cs.interdata.api_backend.service.ThresholdService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 임계값(Threshold), 인벤토리, 임계값 알림 등
 * 주요 웹 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api")
public class WebController {

    private final ThresholdService thresholdService;
    private final MachineInventoryService machineInventoryService;

    @Autowired
    public WebController(ThresholdService thresholdService, MachineInventoryService machineInventoryService) {
        this.thresholdService = thresholdService;
        this.machineInventoryService = machineInventoryService;
    }

    /**
     * [GET] 임계값(Over Threshold) 설정값 조회
     * ex. /api/metrics/threshold-setting
     */
    @GetMapping("/metrics/threshold-setting")
    public ResponseEntity<?> getThreshold() {
        return ResponseEntity.ok(thresholdService.getThreshold());
    }

    /**
     * [POST] 임계값(Over Threshold) 설정값 저장/변경
     * ex. /api/metrics/threshold-setting
     * @param dto 임계값 설정 요청 정보
     */
    @PostMapping("/metrics/threshold-setting")
    public ResponseEntity<?> setThreshold(@RequestBody ThresholdSetting dto) {
        // 서비스로 설정 요청 위임 (에러 발생 시 error 응답)
        ThresholdErrorResponse errorResponse = thresholdService.setThreshold(dto);

        if (errorResponse != null) {
            return ResponseEntity
                    .badRequest()
                    .body(errorResponse);   // 잘못된 입력 경우 400 반환
        }

        // 설정 성공 시 응답 메시지
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /**
     * [GET] 임계값(Under Threshold, 하한) 설정값 조회
     */
    @GetMapping("/metrics/under-threshold-setting")
    public ResponseEntity<?> getUnderThreshold() {
        return ResponseEntity.ok(thresholdService.getUnderThreshold());
    }

    /**
     * [POST] 임계값(Under Threshold, 하한) 설정값 저장/변경
     */
    @PostMapping("/metrics/under-threshold-setting")
    public ResponseEntity<?> setUnderThreshold(@RequestBody ThresholdSetting dto) {
        ThresholdErrorResponse errorResponse = thresholdService.setUnderThreshold(dto);

        if (errorResponse != null) {
            return ResponseEntity
                    .badRequest()
                    .body(errorResponse);
        }

        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /**
     * [POST] 특정 기계의 임계값(Threshold) 변경 이력 조회
     * @param targetId 조회 대상 기계 ID 정보
     */
    @PostMapping("/metrics/threshold-history")
    public ResponseEntity<?> getThresholdHistory(@RequestBody MachineIdforHistory targetId) {
        return ResponseEntity.ok(thresholdService.getThresholdHistoryforMachineId(targetId));
    }

    /**
     * [GET] 전체 기계의 임계값(Threshold) 변경 이력 전체 조회
     */
    @GetMapping("/metrics/threshold-history-all")
    public ResponseEntity<?> getThresholdHistoryAll() {
        return ResponseEntity.ok(thresholdService.getThresholdHistortForAll());
    }

    /**
     * [GET] 전체 기계/컨테이너 인벤토리 리스트 조회
     */
    @GetMapping("/inventory/list")
    public ResponseEntity<?> getInventoryList() {
        return ResponseEntity.ok(machineInventoryService.getHostContainerInventoryList());
    }

    /**
     * [GET] SSE(Server-Sent Events) 방식의 임계값(Threshold) 알림 전송
     * 클라이언트는 /api/metrics/threshold-alert로 SSE 연결, 임계값 이상/이하 알림 실시간 수신
     */
    @GetMapping("/metrics/threshold-alert")
    public SseEmitter alertThreshold() {
        return thresholdService.alertThreshold();
    }

}
