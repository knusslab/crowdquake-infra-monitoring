package kr.cs.interdata.api_backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import kr.cs.interdata.api_backend.dto.*;
import kr.cs.interdata.api_backend.dto.abnormal_log_dto.*;
import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import kr.cs.interdata.api_backend.infra.ThresholdStore;
import kr.cs.interdata.api_backend.service.repository_service.AbnormalDetectionService;
import kr.cs.interdata.api_backend.service.repository_service.ContainerInventoryService;
import kr.cs.interdata.api_backend.service.repository_service.MonitoringDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ThresholdService {

    // 클라이언트의 Emitter를 저장할 ConcurrentHashMap
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> zeroStateCache = new ConcurrentHashMap<>();

    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(ThresholdService.class);

    private final AbnormalDetectionService abnormalDetectionService;
    private final MonitoringDefinitionService monitoringDefinitionService;
    private final ContainerInventoryService containerInventoryService;
    private final ThresholdStore thresholdStore;


    @Autowired
    public ThresholdService(ThresholdStore thresholdStore,
                            AbnormalDetectionService abnormalDetectionService,
                            MonitoringDefinitionService monitoringDefinitionService, ContainerInventoryService containerInventoryService) {
        this.thresholdStore = thresholdStore;
        this.abnormalDetectionService = abnormalDetectionService;
        this.monitoringDefinitionService = monitoringDefinitionService;
        this.containerInventoryService = containerInventoryService;
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
        return monitoringDefinitionService.findThresholdByType("overThresholdValue", "host");
    }

    /**
     *  2. 새로운 임계값을 설정
     * @param dto   각 메트릭의 threshold값
     * @return  ok 메세지를 보낸다.
     */
    public ThresholdErrorResponse setThreshold(ThresholdSetting dto) {
        Map<String, Double> underThresholdMap = new LinkedHashMap<>(thresholdStore.getUnderThresholdValues());

        Map<String, String> underThresholdStrMap = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : underThresholdMap.entrySet()) {
            underThresholdStrMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        try {
            if (Double.parseDouble(dto.getCpuPercent()) < underThresholdMap.get("cpuPercent")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getMemoryUsage()) < underThresholdMap.get("memoryUsage")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getDiskReadDelta()) < underThresholdMap.get("diskReadDelta")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getDiskWriteDelta()) < underThresholdMap.get("diskWriteDelta")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getNetworkRx()) < underThresholdMap.get("networkRx")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getNetworkTx()) < underThresholdMap.get("networkTx")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
            if (Double.parseDouble(dto.getTemperature()) < underThresholdMap.get("temperature")) {
                return new ThresholdErrorResponse(errorMessage("overThresholdValue"), underThresholdStrMap);
            }
        } catch (NumberFormatException e) {
            return new ThresholdErrorResponse("Invalid number format in one or more fields.", underThresholdStrMap);
        }

        // 각 메트릭에 대한 임계값 업데이트
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "cpu", Double.parseDouble(dto.getCpuPercent()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "memory", Double.parseDouble(dto.getMemoryUsage()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "networkTx", Double.parseDouble(dto.getNetworkTx()));
        monitoringDefinitionService.updateThresholdByMetricName("overThresholdValue", "temperature", Double.parseDouble(dto.getTemperature()));

        // 임계값 ThresholdStore에 저장 - over 값
        thresholdStore.updateOverThreshold("host", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateOverThreshold("host", "memory", Double.parseDouble(dto.getMemoryUsage()));
        thresholdStore.updateOverThreshold("host", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        thresholdStore.updateOverThreshold("host", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        thresholdStore.updateOverThreshold("host", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        thresholdStore.updateOverThreshold("host", "networkTx", Double.parseDouble(dto.getNetworkTx()));
        thresholdStore.updateOverThreshold("host", "temperature", Double.parseDouble(dto.getTemperature()));

        thresholdStore.updateOverThreshold("container", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateOverThreshold("container", "memory", Double.parseDouble(dto.getMemoryUsage()));
        thresholdStore.updateOverThreshold("container", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        thresholdStore.updateOverThreshold("container", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        thresholdStore.updateOverThreshold("container", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        thresholdStore.updateOverThreshold("container", "networkTx", Double.parseDouble(dto.getNetworkTx()));

        return null;
    }

    public ThresholdSetting getUnderThreshold() {
        /*
         * "container"와 "host" 타입의 임계값은 같으므로
         * "host" 타입의 임계값을 조회해 가져온다.
         */
        return monitoringDefinitionService.findThresholdByType("underThresholdValue", "host");
    }

    public ThresholdErrorResponse setUnderThreshold(ThresholdSetting dto) {
        Map<String, Double> overThresholdMap = new LinkedHashMap<>(thresholdStore.getOverThresholdValues());

        Map<String, String> overThresholdStrMap = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : overThresholdMap.entrySet()) {
            overThresholdStrMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        try {
            if (Double.parseDouble(dto.getCpuPercent()) > overThresholdMap.get("cpuPercent")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getMemoryUsage()) > overThresholdMap.get("memoryUsage")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getDiskReadDelta()) > overThresholdMap.get("diskReadDelta")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getDiskWriteDelta()) > overThresholdMap.get("diskWriteDelta")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getNetworkRx()) > overThresholdMap.get("networkRx")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getNetworkTx()) > overThresholdMap.get("networkTx")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
            if (Double.parseDouble(dto.getTemperature()) > overThresholdMap.get("temperature")) {
                return new ThresholdErrorResponse(errorMessage("underThresholdValue"), overThresholdStrMap);
            }
        } catch (NumberFormatException e) {
            return new ThresholdErrorResponse("Invalid number format in one or more fields.", overThresholdStrMap);
        }

        // 각 메트릭에 대한 임계값 업데이트
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "cpu", Double.parseDouble(dto.getCpuPercent()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "memory", Double.parseDouble(dto.getMemoryUsage()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "networkTx", Double.parseDouble(dto.getNetworkTx()));
        monitoringDefinitionService.updateThresholdByMetricName("underThresholdValue", "temperature", Double.parseDouble(dto.getTemperature()));

        // 임계값 ThresholdStore에 저장 - under 값
        thresholdStore.updateUnderThreshold("host", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateUnderThreshold("host", "memory", Double.parseDouble(dto.getMemoryUsage()));
        thresholdStore.updateUnderThreshold("host", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        thresholdStore.updateUnderThreshold("host", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        thresholdStore.updateUnderThreshold("host", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        thresholdStore.updateUnderThreshold("host", "networkTx", Double.parseDouble(dto.getNetworkTx()));
        thresholdStore.updateUnderThreshold("host", "temperature", Double.parseDouble(dto.getTemperature()));

        thresholdStore.updateUnderThreshold("container", "cpu", Double.parseDouble(dto.getCpuPercent()));
        thresholdStore.updateUnderThreshold("container", "memory", Double.parseDouble(dto.getMemoryUsage()));
        thresholdStore.updateUnderThreshold("container", "diskReadDelta", Double.parseDouble(dto.getDiskReadDelta()));
        thresholdStore.updateUnderThreshold("container", "diskWriteDelta", Double.parseDouble(dto.getDiskWriteDelta()));
        thresholdStore.updateUnderThreshold("container", "networkRx", Double.parseDouble(dto.getNetworkRx()));
        thresholdStore.updateUnderThreshold("container", "networkTx", Double.parseDouble(dto.getNetworkTx()));

        return null;
    }

    private String errorMessage(String type) {
        if (type.equals("underThresholdValue")) {
            return "A value below the threshold cannot be greater than a value above the threshold.";
        }
        else if (type.equals("overThresholdValue")) {
            return "A value above the threshold cannot be lower than a value below the threshold.";
        }
        else {
            return null;
        }
    }

    /**
     *  3. 특정 machine Id의 이상 로그 이력 조회
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
            record.put("value", log.getValue());
            result.add(record);
        }

        return result;
    }

    /**
     *  4. 모든 머신의 이상 로그 이력 조회
     *
     * @return  이력 리스트 (최신 기준으로 최대 50개)
     */
    public List<Map<String, Object>> getThresholdHistortForAll() {
        // Service를 통해 DB 조회
        List<AbnormalMetricLog> logs = abnormalDetectionService.getLatestAbnormalMetrics();

        // 결과를 클라이언트에 맞게 매핑
        List<Map<String, Object>> result = new ArrayList<>();
        for (AbnormalMetricLog log : logs) {
            Map<String, Object> record = new HashMap<>();

            // ContainerInventory 엔티티의 machineId와 machineName을 파싱해서 둘 조합이 있으면 종속된 hostName을 넘겨줌
            String hostName;
            if (log.getMachineType().equals("container")) {
                hostName = containerInventoryService.addHostNameByContainerIdAndContainerName(
                        log.getMachineId(),
                        log.getMachineName()
                );
            } else {
                hostName = log.getMachineName();
            }

            record.put("timestamp", log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            record.put("messageType", log.getMessageType());
            record.put("machineType", log.getMachineType());
            record.put("machineId", log.getMachineId());
            record.put("machineName", log.getMachineName());
            record.put("metricName", log.getMetricName());
            record.put("hostName", hostName);
            record.put("threshold", log.getThreshold());
            record.put("value", log.getValue());

            result.add(record);
        }

        return result;
    }

    /*
     *  5-1-1. threshold를 넘은 값이 생길 시 이를 처리하는 메서드
     *
     * @param dto
     *        - type            : 이상 로그 발생 머신의 type
     *        - machineId       : 이상 로그 발생 머신의 ID
     *        - machineName     : 이상 로그 발생 머신의 name
     *        - metricName      : (임계초과)메트릭 이름
     *        - value           : 임계값을 넘은 값
     *        - timestamp       : 임계값을 넘은 시각
     */
     public void storeThresholdExceededLog(StoreThresholdViolated dto) {
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

    /*
     *  5-1-2. threshold에 미달된 값이 생길 시 이를 처리하는 메서드
     *
     * @param dto
     *        - type            : 이상 로그 발생 머신의 type
     *        - machineId       : 이상 로그 발생 머신의 ID
     *        - machineName     : 이상 로그 발생 머신의 name
     *        - metricName      : (임계미달)메트릭 이름
     *        - value           : 임계값에 미달된 값
     *        - timestamp       : 임계값에 미달된 시각
     */
    public void storeThresholdDeceededLog(StoreThresholdViolated dto) {
        String machineId = dto.getMachineId();
        String type = dto.getType();
        String machineName = dto.getMachineName();
        String metricName = dto.getMetricName();
        String threshold = dto.getThreshold();
        String value = dto.getValue();
        LocalDateTime timestamp = dto.getTimestamp();

        abnormalDetectionService.storeThresholdDeceeded(
            type,
            machineId,
            machineName,
            metricName,
            threshold,
            value,
            timestamp
        );

        // 실시간 전송 준비
        AlertThresholdDeceeded alert = new AlertThresholdDeceeded();
        alert.setMachineId(machineId);
        alert.setMachineName(machineName);
        alert.setMetricName(metricName);
        alert.setValue(value);
        alert.setThreshold(threshold);
        alert.setTimestamp(timestamp);

        // 실시간 전송 (비동기 처리)
        CompletableFuture.runAsync(() -> publishThresholdDeceeded(alert));

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

    @Scheduled(fixedRate = 5 * 60 * 1000) // 5분마다
    public void cleanUpEmitters() {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name("ping").data("keepalive"));
            } catch (Exception e) {
                emitters.remove(entry.getKey());
                logger.info("Cleaned up dead emitter: {}", entry.getKey());
            }
        }
    }

    @PreDestroy
    public void cleanUpAllEmitters() {
        emitters.forEach((id, emitter) -> emitter.complete());
        emitters.clear();
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
     *  6-2-2. 임계값에 미달된 데이터가 발생하면 실시간으로 전송한다.
     *      -> 이상값이 생길 시, 5번 메서드와 함께 데이터를 처리하며 실행된다.
     *
     * @param alert     실시간 전송할 임계치에 미달된 데이터
     */
    public void publishThresholdDeceeded(AlertThresholdDeceeded alert) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            // 변환에 실패하면 로깅만 하고 기본 메시지 설정
            logger.error("Failed to convert AlertThresholdDeceeded to JSON. Sending default error message.", e);
            jsonData = "{\"error\": \"Failed to convert AlertThresholdDeceeded to JSON\"}";
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
     * 6-2-3. container가 꺼졌다 판단되면 실시간으로 전송한다.
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
     * 6-2-4. container가 꺼졌다 켜진 후, containerId가 바뀌었다고 판단되면 실시간으로 전송한다.
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
     * 6-2-5. 해당 데이터에 대해 1분이상 데이터가 조회되지 않을 시 이에 대한 이상 로그를 실시간으로 전송한다.
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
        String cacheKey = type + ":" + machineId + ":" + machineName;

        // CPU, Memory, DiskReadDelta, DiskWriteDelta
        for (int i = 0;i < 4;i++){
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
                metricName = "diskReadDelta";
                metricValue = metricsNode.has("diskReadBytesDelta")
                        ? metricsNode.get("diskReadBytesDelta").asDouble()
                        : 0.0;
            }
            if (i == 3) {
                metricName = "diskWriteDelta";
                metricValue = metricsNode.has("diskReadBytesDelta")
                    ? metricsNode.get("diskWriteBytesDelta").asDouble()
                    : 0.0;
            }

            if (metricValue == 0.0) {
                zeroValueCnt++;
            } else {
                // 정상값이 들어오면 캐시 해제
                zeroStateCache.remove(cacheKey);
            }

            // 각 메트릭별 threshold를 조회해 초과하면 db저장을 위해 api-backend로 데이터 보낸 후, 로깅함.
            isNormal = evaluateThresholdAndLogViolation(type , machineId, machineName,
                    metricName, metricValue, violationTime);
        }


        // 모든 메트릭이 0일 경우 → 캐시에 없을 때만 로그 저장
        if (zeroValueCnt == 4 && !zeroStateCache.containsKey(cacheKey)) {
            storeZeroValueLog(type, machineId, machineName, violationTime);
            zeroStateCache.put(cacheKey, true);
        }

        // Network
        JsonNode networkNode = metricsNode.path("networkDelta");

        if (!networkNode.isMissingNode() && networkNode.isObject()) {
            // [1] Tx 기준 평가
            metricName = "networkTx";
            Iterator<Map.Entry<String, JsonNode>> txInterfaces = networkNode.fields();
            while (txInterfaces.hasNext()) {
                Map.Entry<String, JsonNode> entry = txInterfaces.next();
                JsonNode interfaceData = entry.getValue();

                metricValue = interfaceData.has("txBytesDelta")
                      ? interfaceData.get("txBytesDelta").asDouble()
                      : 0.0;

                    isNormal = evaluateThresholdAndLogViolation(
                        type, machineId, machineName,
                        metricName, metricValue, violationTime
                    );

                    if (!isNormal) {
                        break; // Tx 기준 비정상이면 루프 종료
                    }
            }

            // [2] Rx 기준 평가
            metricName = "networkRx";
            Iterator<Map.Entry<String, JsonNode>> rxInterfaces = networkNode.fields();
            while (rxInterfaces.hasNext()) {
                Map.Entry<String, JsonNode> entry = rxInterfaces.next();
                JsonNode interfaceData = entry.getValue();

                metricValue = interfaceData.has("rxBytesDelta")
                    ? interfaceData.get("rxBytesDelta").asDouble()
                    : 0.0;

                isNormal = evaluateThresholdAndLogViolation(
                    type, machineId, machineName,
                    metricName, metricValue, violationTime
                );

                if (!isNormal) {
                    break; // Rx 기준 비정상이면 루프 종료
                }
            }
        } else {
            logger.warn("{}: {} - network 데이터를 찾을 수 없습니다.", type, machineId);
        }

        // Temperature
        if (type.equals("host")) {
            metricName = "temperature";

            JsonNode tempsNode = metricsNode.get("temperatures");
            if (tempsNode != null && tempsNode.isObject()) {
                double maxTemp = Double.MIN_VALUE;

                Iterator<Map.Entry<String, JsonNode>> fields = tempsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    double temp = entry.getValue().asDouble();
                    if (temp > maxTemp) {
                        maxTemp = temp;

                    }
                }
                metricValue = maxTemp;
            } else {
                metricValue = 0.0; // fallback
            }

            // threshold를 조회해 초과하면 DB에 저장 후, 로깅함.
            isNormal = evaluateThresholdAndLogViolation(
                type, machineId, machineName,
                metricName, metricValue, violationTime
            );
        }


    }

    /**
     * 주어진 메트릭 값이 임계값(threshold)을 초과 및 미달했는지 판단하고,
     * 초과 시 로그 출력 및 위반 기록을 저장합니다.
     *
     * @param type           대상 종류 (예: host, container 등)
     * @param machineId      대상 ID (hostId 또는 containerId)
     * @param metricName     메트릭 이름 (예: cpuUsagePercent 등)
     * @param value          현재 측정된 메트릭 값
     * @param violationTime  측정 시각 또는 위반 발생 시각
     * @return true  - 임계값 미초과 또는 미미달 또는 임계값이 없음<br>
     *         false - 임계값 초과 및 미달 (위반 저장됨)
     */
    public boolean evaluateThresholdAndLogViolation(String type, String machineId, String machineName,
                                    String metricName, Double value, LocalDateTime violationTime) {
        // thresholdStore에서 해당 메트릭의 임계값을 조회
        Double overThreshold = thresholdStore.getOverThreshold(type, metricName);
        Double underThreshold = thresholdStore.getUnderThreshold(type, metricName);

        // 1. 임계값이 존재하고, 메트릭이 임계값을 초과한 경우
        if (overThreshold != null && value > overThreshold) {
            logger.warn("임계값 초과: {} | {} | {} -> {} = {} (임계값: {})"
                    , type, machineId, machineName, metricName, value, overThreshold);

            // 위반 정보 객체 생성 및 필드 설정
            StoreThresholdViolated storeThresholdViolated = new StoreThresholdViolated();
            storeThresholdViolated.setType(type);
            storeThresholdViolated.setMachineId(machineId);
            storeThresholdViolated.setMachineName(machineName);
            storeThresholdViolated.setMetricName(metricName);
            storeThresholdViolated.setValue(String.valueOf(value));
            storeThresholdViolated.setThreshold(String.valueOf(overThreshold));
            storeThresholdViolated.setTimestamp(violationTime);

            // 위반 기록 저장
            storeThresholdExceededLog(storeThresholdViolated);

            return false;
        }
        // 2. 임계값 존재하고, 메트릭이 임계값에 미달된 경우
        else if (underThreshold != null && (value < underThreshold)) {
            logger.warn("임계값 미달: {} | {} | {} -> {} = {} (임계값: {})"
                , type, machineId, machineName, metricName, value, underThreshold);

            // 위반 정보 객체 생성 및 필드 설정
            StoreThresholdViolated storeThresholdViolated = new StoreThresholdViolated();
            storeThresholdViolated.setType(type);
            storeThresholdViolated.setMachineId(machineId);
            storeThresholdViolated.setMachineName(machineName);
            storeThresholdViolated.setMetricName(metricName);
            storeThresholdViolated.setValue(String.valueOf(value));
            storeThresholdViolated.setThreshold(String.valueOf(underThreshold));
            storeThresholdViolated.setTimestamp(violationTime);

            // 위반 기록 저장
            storeThresholdDeceededLog(storeThresholdViolated);

            return false;
        }
        // 3.
        else if (overThreshold != null && underThreshold != null) {
            // 임계값에 미달되거나 초과하지 않음 -> 아무것도 하지 않는 상태
        }
        // 4. 임계값 자체가 존재하지 않는 경우
        else {
            logger.warn("임계값이 조회되지 않았습니다.");
        }

        // 임계값을 초과하지 않았거나, 임계값에 미달되지 않았거나, 임계값이 존재하지 않을 때 true 반환
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
