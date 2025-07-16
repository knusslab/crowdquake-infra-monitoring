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

    @GetMapping("/metrics/threshold-setting")
    public ResponseEntity<?> getThreshold() {
        return ResponseEntity.ok(thresholdService.getThreshold());
    }

    @PostMapping("/metrics/threshold-setting")
    public ResponseEntity<?> setThreshold(@RequestBody ThresholdSetting dto) {
        ThresholdErrorResponse errorResponse = thresholdService.setThreshold(dto);

        if (errorResponse != null) {
            return ResponseEntity
                    .badRequest()
                    .body(errorResponse);
        }

        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @GetMapping("/metrics/under-threshold-setting")
    public ResponseEntity<?> getUnderThreshold() {
        return ResponseEntity.ok(thresholdService.getUnderThreshold());
    }

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

    @PostMapping("/metrics/threshold-history")
    public ResponseEntity<?> getThresholdHistory(@RequestBody MachineIdforHistory targetId) {
        return ResponseEntity.ok(thresholdService.getThresholdHistoryforMachineId(targetId));
    }

    @GetMapping("/metrics/threshold-history-all")
    public ResponseEntity<?> getThresholdHistoryAll() {
        return ResponseEntity.ok(thresholdService.getThresholdHistortForAll());
    }

    @GetMapping("/inventory/list")
    public ResponseEntity<?> getInventoryList() {
        return ResponseEntity.ok(machineInventoryService.getHostContainerInventoryList());
    }

    // SSE 방식
    @GetMapping("/metrics/threshold-alert")
    public SseEmitter alertThreshold() {
        return thresholdService.alertThreshold();
    }

}
