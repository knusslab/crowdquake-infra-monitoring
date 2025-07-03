package kr.cs.interdata.api_backend.controller;

import kr.cs.interdata.api_backend.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import kr.cs.interdata.api_backend.service.ThresholdService;

import kr.cs.interdata.api_backend.dto.StoreViolation;

@RestController
@RequestMapping("/api")
public class MetricConsumerController {

    private final ThresholdService thresholdService;
    private final InventoryService inventoryService;

    @Autowired
    public MetricConsumerController(ThresholdService thresholdService, InventoryService inventoryService) {
        this.thresholdService = thresholdService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/metrics/threshold-check")
    public ResponseEntity<?> checkThreshold() {
        return ResponseEntity.ok(thresholdService.checkThreshold());
    }

    @PostMapping("/violation-store")
    public ResponseEntity<?> storeViolation(@RequestBody StoreViolation dto) {
        return ResponseEntity.ok(thresholdService.storeViolation(dto));
    }

    @GetMapping("/inventory/{machineId}/{type}")
    public String getOrGenerateUniqueId(@PathVariable String machineId, @PathVariable String type) {
        return inventoryService.getOrGenerateUniqueId(machineId, type);
    }
}
