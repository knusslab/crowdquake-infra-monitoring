package kr.cs.interdata.api_backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import kr.cs.interdata.api_backend.service.repository_service.MachineInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    @Autowired
    private Cache<String, String> inventoryCache;
    @Autowired
    private MachineInventoryService machineInventoryService;

    private final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    /**
     *  - 주어진 machineId와 type에 대해 고유한 targetId를 반환한다.
     *
     * <p>동작 순서:
     * <ol>
     *   <li>먼저 캐시(inventoryCache)에서 machineId에 해당하는 targetId를 조회한다.</li>
     *   <li>캐시에 존재하면 해당 targetId를 반환한다.</li>
     *   <li>캐시에 없으면 DB에서 조회 및 필요 시 id를 생성하여 targetId를 획득한다.</li>
     *   <li>새로 조회/생성한 targetId를 캐시에 저장한다.</li>
     *   <li>최종적으로 targetId를 반환한다.</li>
     * </ol>
     *
     * @param machineId 고유 id를 생성하거나 조회할 대상 machine의 ID
     * @param type      대상 machine의 타입
     * @return  machineId와 type에 해당하는 고유한 targetId
     */
    public String getOrGenerateUniqueId(String machineId, String type) {
        // 1. Cache에서 먼저 조회
        String targetId = inventoryCache.getIfPresent(machineId);

        // 2. 만약 targetId가 성공적으로 조회되었다면 이를 리턴.
        if (targetId != null) {
            logger.info("targetId(viewed) - {}", targetId); //TODO:지우기
            return targetId;
        }

        // 3. DB에서 조회 및 id 생성
        targetId = machineInventoryService.changeMachineIdToTargetId(type, machineId);
        logger.info("targetId(created) - {}", targetId); //TODO:지우기

        // 4. Cache에 추가
        inventoryCache.put(machineId, targetId);

        // 5. Cache에 추가 후, targetId를 리턴
        return targetId;
    }
}
