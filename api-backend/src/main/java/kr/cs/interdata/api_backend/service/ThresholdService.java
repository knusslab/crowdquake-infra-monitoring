package kr.cs.interdata.api_backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.api_backend.dto.*;
import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import kr.cs.interdata.api_backend.service.repository_service.AbnormalDetectionService;
import kr.cs.interdata.api_backend.service.repository_service.MachineInventoryService;
import kr.cs.interdata.api_backend.service.repository_service.MonitoringDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ThresholdService {

    // 클라이언트의 Emitter를 저장할 ConcurrentHashMap
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(ThresholdService.class);

    private final AbnormalDetectionService abnormalDetectionService;
    private final MonitoringDefinitionService monitoringDefinitionService;
    private final MachineInventoryService machineInventoryService;

    @Autowired
    public ThresholdService(AbnormalDetectionService abnormalDetectionService,
                            MonitoringDefinitionService monitoringDefinitionService,
                            MachineInventoryService machineInventoryService) {
        this.abnormalDetectionService = abnormalDetectionService;
        this.monitoringDefinitionService = monitoringDefinitionService;
        this.machineInventoryService = machineInventoryService;
    }

    /**
     *  1. 현재 설정된 임계값을 조회
     * @return  각 메트릭의 임계값을 담은 데이터를 리턴한다.
     */
    public ThresholdSetting getThreshold() {
        /*
         * "container"와 "host" 타입의 임계값은 같으므로
         * "host" 타입의 임계값을 조회해 가져온다.
         */
        return monitoringDefinitionService.findThresholdByType("host");
    }

    /**
     *  2. 새로운 임계값을 설정
     * @param dto   각 메트릭의 threshold값
     * @return  ok 메세지를 보낸다.
     */
    public Map<String, String> setThreshold(ThresholdSetting dto) {
        // 각 메트릭에 대한 임계값 업데이트
        monitoringDefinitionService.updateThresholdByMetricName("cpu", Double.parseDouble(dto.getCpuPercent()));
        monitoringDefinitionService.updateThresholdByMetricName("memory", Double.parseDouble(dto.getMemoryPercent()));
        monitoringDefinitionService.updateThresholdByMetricName("disk", Double.parseDouble(dto.getDiskPercent()));
        monitoringDefinitionService.updateThresholdByMetricName("network", Double.parseDouble(dto.getNetworkTraffic()));

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("message", "ok");
        return response;
    }

    /**
     * 3. 특정 날짜의 임계값 초과 이력 조회
     * @param date 조회할 날짜 정보
     * @return 이력 리스트
     */
    public List<Map<String, Object>> getThresholdHistory(DateforHistory date) {
        // Service를 통해 DB 조회
        List<AbnormalMetricLog> logs = abnormalDetectionService.getLatestAbnormalMetricsByDate(date.getDate());

        // 결과를 클라이언트에 맞게 매핑
        List<Map<String, Object>> result = new ArrayList<>();
        for (AbnormalMetricLog log : logs) {
            Map<String, Object> record = new HashMap<>();
            record.put("timestamp", log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            record.put("targetId", log.getTargetId());
            record.put("metricName", log.getMetricName());
            record.put("value", log.getValue().toString());
            result.add(record);
        }

        return result;
    }

    /**
     *  4. threshold 조회
     *  -> MetricsByType 테이블의 모든 값을 조회해,
     *      모든 타입의 모든 metric의 threshold를 Map의 형태로 저장하여 return한다.
     *
     * @return Map<type(String), Map<metric_name(String), threshold(Double)>> resultMap
     */
    public Map<String, Map<String, Double>> checkThreshold() {
        // MonitoringDefinitionService에서 조회
        return monitoringDefinitionService.findAllThresholdsGroupedByType();
    }

    /**
     *  5. threshold를 넘은 값이 생길 시 이를 처리하는 메서드
     *    -> consumer에서 임계값을 조회해 이 프로젝트로 넘어왔을 때, 이는 임계값을 넘은 값이고,
     *          1. 이를 db에 저장한다.
     *          2. 이를 sse방식으로 실시간 전송하는 메서드(6-2)를 호출한다.
     *    -> db : AbnormalMetricLog, LatestAbnormalStatus
     * @param dto
     *        - typeId     : 메시지를 보낸 호스트
     *        - metricName   : 메트릭 이름
     *        - value    : 임계값을 넘은 값
     *        - timestamp: 임계값을 넘은 시각
     */
    public Object storeViolation(StoreViolation dto) {
        //이상값이 생긴 로그를 저장한다.
        String type = dto.getType();
        String machineId = dto.getMachineId();
        String metricName = dto.getMetricName();
        String value = dto.getValue();
        LocalDateTime timestamp = dto.getTimestamp();

        // machine_id로 넘어온 id를 고유id로 바꿔 저장한다.
        String targetId = machineInventoryService.changeMachineIdToTargetId(type, machineId);

        abnormalDetectionService.storeViolation(
                targetId,
                metricName,
                value,
                timestamp
        );

        // 실시간 전송 준비
        AlertThreshold alert = new AlertThreshold();
        alert.setTargetId(targetId);
        alert.setMetricName(metricName);
        alert.setValue(value);
        alert.setTimestamp(timestamp);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishThreshold(alert));

        //단, request에 대한 응답값은 없다.
        return "ok";
    }

    /**
     *  6-1. sse방식을 사용하기 위해 비동기로 emitter를 연결한다.
     *  -> SSE 연결을 생성하고 Emitter를 관리한다.
     *
     * @return emitter 연결
     */
    public SseEmitter alertThreshold() {
        String emitterId = "emitter_" + System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Emitter 저장
        emitters.put(emitterId, emitter);

        // 연결이 끊어지면 맵에서 제거
        // 즉, 클라이언트가 페이지를 벗어나거나 연결을 끊으면, SseEmitter의 콜백이 실행됨.
        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));
        emitter.onError((e) -> emitters.remove(emitterId));

        logger.info("Client Connected: {}", emitterId);
        return emitter;
    }


    /**
     *  6-2. 임계값을 초과한 데이터가 발생하면 실시간으로 전송한다.
     *      -> 이상값이 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert     실시간 전송할 임계치를 넘은 데이터
     */
    public void publishThreshold(AlertThreshold alert) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertThreshold to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertThreshold to JSON\"}";
        }

        // 모든 Emitter에 브로드캐스트 전송
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(jsonData);
            } catch (IOException e) {
                logger.warn("Failed to send data to client. Removing emitter: " + entry.getKey());
                entry.getValue().completeWithError(e);
                emitters.remove(entry.getKey());
            }
        }
    }

    //test
    public Object hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "hello!");
        return response;
    }

}
