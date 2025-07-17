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

    /**
     * 외부 consumer가 메트릭 데이터를 POST로 전달할 때 호출되는 API 엔드포인트입니다.
     *
     * @param metric JSON 등 문자열 형태의 메트릭 데이터
     * @return 반환은 하지만 전달하지 않음 (별도의 본문 없음)
     */
    @PostMapping("/metrics")
    public ResponseEntity<Void> sendMetrics(@RequestBody String metric) {
        metricService.sendMetric(metric);
        return ResponseEntity.ok().build();
    }


}
