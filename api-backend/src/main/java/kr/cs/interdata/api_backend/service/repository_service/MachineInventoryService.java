package kr.cs.interdata.api_backend.service.repository_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.api_backend.entity.ContainerInventory;
import kr.cs.interdata.api_backend.entity.HostInventory;
import kr.cs.interdata.api_backend.entity.TargetType;
import kr.cs.interdata.api_backend.repository.ContainerInventoryRepository;
import kr.cs.interdata.api_backend.repository.HostInventoryRepository;
import kr.cs.interdata.api_backend.repository.TargetTypeRepository;
import kr.cs.interdata.api_backend.service.ThresholdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.lang.annotation.Target;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;


@Service
public class MachineInventoryService {

    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();

    public final TargetTypeRepository targetTypeRepository;
    private final HostInventoryRepository hostInventoryRepository;
    private final ContainerInventoryRepository containerInventoryRepository;
    private final Logger logger = LoggerFactory.getLogger(MachineInventoryService.class);

    @Autowired
    public MachineInventoryService(TargetTypeRepository targetTypeRepository,
                                   HostInventoryRepository hostInventoryRepository,
                                   ContainerInventoryRepository containerInventoryRepository) {
        this.targetTypeRepository = targetTypeRepository;
        this.hostInventoryRepository = hostInventoryRepository;
        this.containerInventoryRepository = containerInventoryRepository;
    }

    /**
     *  - 파라미터로 준 metric data에서의 머신 정보들의 DB 존재 여부 판별 및 삽입 메서드
     *
     * @param metricData    metric data
     */
    @Async
    public void registerMachineIfAbsent(String metricData) {
        JsonNode root = parseJson(metricData);

        if (root == null) {
            logger.error("Null parameter detected - metric data: {}", root);
            return;
        }

        String hostId = root.path("hostId").asText();   // host id
        String hostName = root.path("name").asText();   // host name

        // hostInventory에 있는지 없는지 판별 없으면 삽입
        if (!hostInventoryRepository.existsByHostIdAndHostName(hostId, hostName)) {
            Optional<TargetType> optionalType = targetTypeRepository.findByType("host");
            TargetType type;
            if (optionalType.isPresent()) {
                type = optionalType.get();
            } else {
                // 없으면 새로 생성
                type = TargetType.builder().type("host").build();
                targetTypeRepository.save(type);
            }

            // HostInventory에 저장
            HostInventory hostInventory = new HostInventory();
            hostInventory.setType(type); 
            hostInventory.setHostId(hostId);
            hostInventory.setHostName(hostName);
            hostInventoryRepository.save(hostInventory);
        }

        JsonNode containersNode = root.path("containers");
        if (containersNode != null && containersNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = containersNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String containerId = entry.getKey();             // container id
                JsonNode containerNode = entry.getValue();       // 그 안의 메트릭 정보

                String containerName = containerNode.path("name").asText(); // ex. "app1"

                // containerInventory에 있는지 없는지 판별
                if(!containerInventoryRepository.existsByHostNameAndContainerIdAndContainerName(
                        hostName,
                        containerId,
                        containerName))
                {
                    Optional<TargetType> optionalType = targetTypeRepository.findByType("container");
                    TargetType type;
                    if (optionalType.isPresent()) {
                        type = optionalType.get();
                    } else {
                        // 없으면 새로 생성
                        type = TargetType.builder().type("container").build();
                        targetTypeRepository.save(type);
                    }

                    // containerInventory에 저장
                    ContainerInventory containerInventory = new ContainerInventory();
                    containerInventory.setType(type);
                    containerInventory.setHostName(hostName);
                    containerInventory.setContainerId(containerId);
                    containerInventory.setContainerName(containerName);
                    containerInventoryRepository.save(containerInventory);
                }
            }
        }
    }

    // 머신 모든 숫자 조회
    public int retrieveAllMachineNumber() {
        int result = 0;
        result = hostInventoryRepository.countAll() + containerInventoryRepository.countAll();

        return result;
    }

    // "type"을 입력한 타입의 머신 총 숫자 조회
    public int retrieveMachineNumberByType(String type) {
        int result = 0;

        if (type.equals("host")) {
            result = hostInventoryRepository.countAll();
        }
        else if (type.equals("container")) {
            result = containerInventoryRepository.countAll();
        }
        else {
            logger.error("{} is not a valid machine type", type);
        }

        return result;
    }

    // json 파싱
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new ThresholdService.InvalidJsonException("JSON 파싱 실패", e);
        }
    }

    // 사용자 정의 예외
    public static class InvalidJsonException extends RuntimeException {
        public InvalidJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
