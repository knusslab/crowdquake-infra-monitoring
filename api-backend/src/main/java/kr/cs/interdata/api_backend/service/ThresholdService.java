package kr.cs.interdata.api_backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.api_backend.dto.*;
import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import kr.cs.interdata.api_backend.service.repository_service.AbnormalDetectionService;
import kr.cs.interdata.api_backend.service.repository_service.MonitoringDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
    private final ThresholdStore thresholdStore;


    @Autowired
    public ThresholdService(ThresholdStore thresholdStore,
                            AbnormalDetectionService abnormalDetectionService,
                            MonitoringDefinitionService monitoringDefinitionService) {
        this.thresholdStore = thresholdStore;
        this.abnormalDetectionService = abnormalDetectionService;
        this.monitoringDefinitionService = monitoringDefinitionService;
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

        // 임계값 ThresholdStore에 저장
        thresholdStore.updateThreshold("host", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateThreshold("host", "memory", Double.parseDouble(dto.getMemoryPercent()));
        thresholdStore.updateThreshold("host", "disk", Double.parseDouble(dto.getDiskPercent()));
        thresholdStore.updateThreshold("host", "network", Double.parseDouble(dto.getNetworkTraffic()));

        thresholdStore.updateThreshold("container", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateThreshold("container", "memory", Double.parseDouble(dto.getMemoryPercent()));
        thresholdStore.updateThreshold("container", "disk", Double.parseDouble(dto.getDiskPercent()));
        thresholdStore.updateThreshold("container", "network", Double.parseDouble(dto.getNetworkTraffic()));

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("message", "ok");
        return response;
    }

    /**
     *  3. 특정 machine Id의 임계값 초과 이력 조회
     * @param machineId  조회할 machine Id
     * @return  이력 리스트
     */
    public List<Map<String, Object>> getThresholdHistoryforMachineId(MachineIdforHistory machineId) {

        // Service를 통해 DB 조회
        List<AbnormalMetricLog> logs = abnormalDetectionService.getLatestAbnormalMetricsByMachineId(machineId.getTargetId());

        // 결과를 클라이언트에 맞게 매핑
        List<Map<String, Object>> result = new ArrayList<>();
        for (AbnormalMetricLog log : logs) {
            Map<String, Object> record = new HashMap<>();
            record.put("timestamp", log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            record.put("targetId", log.getMachineId());
            record.put("metricName", log.getMetricName());
            record.put("threshold", log.getThreshold());
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

    /*
     *  5-1. threshold를 넘은 값이 생길 시 이를 처리하는 메서드
     *
     * @param dto
     *        - type            : 이상 로그 발생 머신의 type
     *        - machineId       : 이상 로그 발생 머신의 ID
     *        - machineName     : 이상 로그 발생 머신의 name
     *        - metricName      : (임계초과)메트릭 이름
     *        - value           : 임계값을 넘은 값
     *        - timestamp       : 임계값을 넘은 시각
     */
     public void storeThresholdExceededLog(StoreThresholdExceeded dto) {
        String machineId = dto.getMachineId();
        String type = dto.getType();
        String machineName = dto.getMachineName();
        String metricName = dto.getMetricName();
        String threshold = dto.getThreshold();
        String value = dto.getValue();
        LocalDateTime timestamp = dto.getTimestamp();

        abnormalDetectionService.storeThresholdExceeded(
                type,
                machineId,
                machineName,
                metricName,
                threshold,
                value,
                timestamp
        );

        // 실시간 전송 준비
        AlertThresholdExceeded alert = new AlertThresholdExceeded();
        alert.setMachineId(machineId);
        alert.setMachineName(machineName);
        alert.setMetricName(metricName);
        alert.setValue(value);
        alert.setThreshold(threshold);
        alert.setTimestamp(timestamp);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishThresholdExceeded(alert));

    }

    /**
     *  5-2. container가 꺼졌다 판단되면 이상로그를 발생시키고 이를 처리하는 메서드
     *
     * @param type          이상 로그 발생 머신의 type
     * @param machineId     이상 로그 발생 머신의 ID
     * @param machineName   이상 로그 발생 머신의 name
     * @param violationTime 이상 로그가 발생한 시각
     */
    public void storeZeroValueLog(String type, String machineId, String machineName, LocalDateTime violationTime) {
        abnormalDetectionService.storeZeroValue(
                type,
                machineId,
                machineName,
                violationTime);

        // 실시간 전송 준비
        AlertZerovalue alertZerovalue = new AlertZerovalue();
        alertZerovalue.setMachineId(machineId);
        alertZerovalue.setMachineName(machineName);
        alertZerovalue.setTimestamp(violationTime);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishZeroValue(alertZerovalue));
    }

    /**
     *  5-3. container가 꺼졌다 켜진 후, containerId가 바뀌었다고 판단되면 이상로그를 발생시키고 이를 처리하는 메서드
     *
     * @param containerId       이상 로그 발생 머신의 ID
     * @param containerName     이상 로그 발생 머신의 name
     * @param violationTime     이상 로그가 발생한 시각
     */
    public void storeContainerIdChanged(String containerId, String containerName, LocalDateTime violationTime) {
        abnormalDetectionService.storeContainerIdChanged(
                "container",
                containerId,
                containerName,
                violationTime
        );

        // 실시간 전송 준비
        AlertContainerIdChanged alertContainerIdChanged = new AlertContainerIdChanged();
        alertContainerIdChanged.setMachineId(containerId);
        alertContainerIdChanged.setMachineName(containerName);
        alertContainerIdChanged.setTimestamp(violationTime);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishContainerIdChanged(alertContainerIdChanged));
    }

    /**
     *  5-5. 해당 데이터에 대해 1분이상 데이터가 조회되지 않을 시 이에 대한 이상 로그를 발생시키고 이를 처리하는 메서드
     *
     * @param type              이상 로그 발생 머신의 type
     * @param machineId         이상 로그 발생 머신의 ID
     * @param machineName       이상 로그 발생 머신의 name
     * @param violationTime     이상 로그가 발생한 시각
     */
    public void storeTimeout(String type, String machineId, String machineName, LocalDateTime violationTime) {
        abnormalDetectionService.storeTimeout(
                type,
                machineId,
                machineName,
                violationTime
        );

        // 실시간 전송 준비
        AlertTimeout alertTimeout = new AlertTimeout();
        alertTimeout.setMachineId(machineId);
        alertTimeout.setMachineName(machineName);
        alertTimeout.setTimestamp(violationTime);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishTimeout(alertTimeout));
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
     *  6-2-1. 임계값을 초과한 데이터가 발생하면 실시간으로 전송한다.
     *      -> 이상값이 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert     실시간 전송할 임계치를 넘은 데이터
     */
    public void publishThresholdExceeded(AlertThresholdExceeded alert) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertThresholdExceeded to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertThresholdExceeded to JSON\"}";
        }

        // 모든 Emitter에 브로드캐스트 전송
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(jsonData);
            } catch (IOException e) {
                logger.warn("Failed to send data to client. Removing emitter: {}", entry.getKey());
                entry.getValue().completeWithError(e);
                emitters.remove(entry.getKey());
            }
        }
    }

    /**
     *
     * 6-2-2. container가 꺼졌다 판단되면 실시간으로 전송한다.
     *      -> 이상로그가 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert  실시간 전송할 데이터
     */
    public void publishZeroValue(AlertZerovalue alert) {
        String jsonData;

        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertZerovalue to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertZerovalue to JSON\"}";
        }

        // 모든 Emitter에 브로드캐스트 전송
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(jsonData);
            } catch (IOException e) {
                logger.warn("Failed to send data to client. Removing emitter: {}", entry.getKey());
                entry.getValue().completeWithError(e);
                emitters.remove(entry.getKey());
            }
        }
    }

    /**
     *
     * 6-2-3. container가 꺼졌다 켜진 후, containerId가 바뀌었다고 판단되면 실시간으로 전송한다.
     *      -> 이상로그가 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert 실시간 전송할 데이터
     */
    public void publishContainerIdChanged(AlertContainerIdChanged alert) {
        String jsonData;

        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertContainerIdChanged to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertContainerIdChanged to JSON\"}";
        }

        // 모든 Emitter에 브로드캐스트 전송
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(jsonData);
            } catch (IOException e) {
                logger.warn("Failed to send data to client. Removing emitter: {}", entry.getKey());
                entry.getValue().completeWithError(e);
                emitters.remove(entry.getKey());
            }
        }
    }

    /**
     * 6-2-4. 해당 데이터에 대해 1분이상 데이터가 조회되지 않을 시 이에 대한 이상 로그를 실시간으로 전송한다.
     *      -> 이상로그가 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert 실시간 전송할 데이터
     */
    public void publishTimeout(AlertTimeout alert) {
        String jsonData;

        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertTimeout to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertTimeout to JSON\"}";
        }

        // 모든 Emitter에 브로드캐스트 전송
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(jsonData);
            } catch (IOException e) {
                logger.warn("Failed to send data to client. Removing emitter: {}", entry.getKey());
                entry.getValue().completeWithError(e);
                emitters.remove(entry.getKey());
            }
        }
    }


    /**
     *  - threshold를 통해 메트릭의 각 값을 계산하는 메서드
     *
     * @param metric    모든 메트릭이 들어있는 데이터
     */
    @Async
    public void calcThreshold(String metric) {
        JsonNode root = parseJson(metric);

        String type = root.path("type").asText();       // "host"
        String hostId = root.path("hostId").asText();   // host id
        String hostName = root.path("name").asText();   // host name
        String violationTime = root.path("timeStamp").asText(); // timestamp

        // 1. Host 자체 메트릭 처리
        processMetricAnomaly(
                type,                  // "host"
                hostId,                // host id
                hostName,                  // hostName
                LocalDateTime.parse(violationTime),
                root                   // 전체 JSON에서 host 메트릭은 root 자체
        );

        // 2. Container 각각 메트릭 처리
        JsonNode containersNode = root.path("containers");
        if (containersNode != null && containersNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = containersNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String containerId = entry.getKey();             // container id
                JsonNode containerNode = entry.getValue();       // 그 안의 메트릭 정보

                String containerName = containerNode.path("name").asText(); // ex. "app1"

                processMetricAnomaly(
                        "container",
                        containerId,
                        containerName,
                        LocalDateTime.parse(violationTime),
                        containerNode
                );
            }
        }
    }


    public void processMetricAnomaly(String type, String machineId, String machineName, LocalDateTime violationTime, JsonNode metricsNode) {
        double metricValue = 0.0;
        int zeroValueCnt = 0;
        String metricName = null;
        boolean isNormal;

        // CPU, Memory, Disk
        for (int i = 0;i < 3;i++){
            if (i == 0) {
                metricName = "cpu";
                metricValue = metricsNode.has("cpuUsagePercent")
                        ? metricsNode.get("cpuUsagePercent").asDouble()
                        : 0.0;
            }
            if (i == 1) {
                metricName = "memory";
                metricValue = metricsNode.has("memoryUsedBytes")
                        ? metricsNode.get("memoryUsedBytes").asDouble()
                        : 0.0;
            }
            if (i == 2) {
                metricName = "disk";
                double diskReadBytesDelta = metricsNode.has("diskReadBytesDelta")
                        ? metricsNode.get("diskReadBytesDelta").asDouble()
                        : 0.0;
                double diskWriteBytesDelta = metricsNode.has("diskWriteBytesDelta")
                        ? metricsNode.get("diskWriteBytesDelta").asDouble()
                        : 0.0;
                metricValue = diskReadBytesDelta + diskWriteBytesDelta;
            }
            if (metricValue == 0.0) { zeroValueCnt++ ; continue;}

            // 각 메트릭별 threshold를 조회해 초과하면 db저장을 위해 api-backend로 데이터 보낸 후, 로깅함.
            isNormal = evaluateThresholdAndLogViolation(type , machineId, machineName,
                    metricName, metricValue, violationTime);
        }

        if (zeroValueCnt == 3) {
            storeZeroValueLog(type, machineId, machineName, violationTime);
        }

        // Network
        JsonNode networkNode = metricsNode.path("networkDelta");

        metricName = "network";
        if (!networkNode.isMissingNode() && networkNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> interfaces = networkNode.fields();
            while (interfaces.hasNext()) {
                Map.Entry<String, JsonNode> entry = interfaces.next();

                JsonNode interfaceData = entry.getValue();

                double txBytesDelta = interfaceData.has("txBytesDelta")
                        ? interfaceData.get("txBytesDelta").asDouble()
                        : 0.0;
                double rxBytesDelta = interfaceData.has("rxBytesDelta")
                        ? interfaceData.get("rxBytesDelta").asDouble()
                        : 0.0;
                metricValue = txBytesDelta + rxBytesDelta;

                // 각 메트릭별 threshold를 조회해 초과하면 DB에 저장 후, 로깅함.
                isNormal = evaluateThresholdAndLogViolation(type , machineId, machineName,
                        metricName, metricValue, violationTime);

                // 만약 어느 network에서 비정상값이 존재한다면
                // 비정상적인 네트워크가 존재한다는 것만 알리고 iteration을 중단한다.
                if (!isNormal) {
                    break; // 하나라도 이상하면 network는 중단
                }
            }
        } else {
            logger.warn("{}: {} - network 데이터를 찾을 수 없습니다.", type, machineId);
        }
    }

    /**
     * 주어진 메트릭 값이 임계값(threshold)을 초과했는지 판단하고,
     * 초과 시 로그 출력 및 위반 기록을 저장합니다.
     *
     * @param type           대상 종류 (예: host, container 등)
     * @param machineId      대상 ID (hostId 또는 containerId)
     * @param metricName     메트릭 이름 (예: cpuUsagePercent 등)
     * @param value          현재 측정된 메트릭 값
     * @param violationTime  측정 시각 또는 위반 발생 시각
     * @return true  - 임계값 미초과 또는 임계값이 없음<br>
     *         false - 임계값 초과 (위반 저장됨)
     */
    public boolean evaluateThresholdAndLogViolation(String type, String machineId, String machineName,
                                    String metricName, Double value, LocalDateTime violationTime) {
        // thresholdStore에서 해당 메트릭의 임계값을 조회
        Double threshold = thresholdStore.getThreshold(type, metricName);

        // 1. 임계값이 존재하고, 메트릭이 임계값을 초과한 경우
        if (threshold != null && value > threshold) {
            logger.warn("임계값 초과: {} | {} | {} -> {} = {} (임계값: {})"
                    , type, machineId, machineName, metricName, value, threshold);

            // 위반 정보 객체 생성 및 필드 설정
            StoreThresholdExceeded storeThresholdExceeded = new StoreThresholdExceeded();
            storeThresholdExceeded.setType(type);
            storeThresholdExceeded.setMachineId(machineId);
            storeThresholdExceeded.setMachineName(machineName);
            storeThresholdExceeded.setMetricName(metricName);
            storeThresholdExceeded.setValue(String.valueOf(value));
            storeThresholdExceeded.setThreshold(String.valueOf(threshold));
            storeThresholdExceeded.setTimestamp(violationTime);

            // 위반 기록 저장
            storeThresholdExceededLog(storeThresholdExceeded);

            return false;
        }

        // 2. 임계값은 존재하지만, 메트릭이 초과하지 않은 경우
        else if (threshold != null && (value < threshold || value.equals(threshold))) {
            // 조건 충족하지 않음 → 아무 작업도 하지 않음
        }

        // 3. 임계값 자체가 존재하지 않는 경우
        else {
            logger.warn("임계값이 조회되지 않았습니다.");
        }

        // 임계값을 초과하지 않았거나, 임계값이 존재하지 않을 때 true 반환
        return true;
    }


    // json 파싱
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new InvalidJsonException("JSON 파싱 실패", e);
        }
    }

    // 사용자 정의 예외
    public static class InvalidJsonException extends RuntimeException {
        public InvalidJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
