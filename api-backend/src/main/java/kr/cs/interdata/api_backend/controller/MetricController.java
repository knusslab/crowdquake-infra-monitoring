package kr.cs.interdata.api_backend.controller;

import kr.cs.interdata.api_backend.service.MetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MetricController {

    private final MetricService metricService;

    @Autowired
    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    // consumer -> api-backend
    @PostMapping("/metrics")
    public ResponseEntity<?> sendMetrics(@RequestBody String metric) {
        return ResponseEntity.ok(metricService.sendMetric(metric));
    }


}
