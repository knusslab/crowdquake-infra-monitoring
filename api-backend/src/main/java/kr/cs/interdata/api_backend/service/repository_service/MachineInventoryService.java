package kr.cs.interdata.api_backend.service.repository_service;

import kr.cs.interdata.api_backend.entity.MonitoredMachineInventory;
import kr.cs.interdata.api_backend.entity.TargetType;
import kr.cs.interdata.api_backend.repository.MonitoredMachineInventoryRepository;
import kr.cs.interdata.api_backend.repository.TargetTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MachineInventoryService {

    private final Logger logger = LoggerFactory.getLogger(MonitoringDefinitionService.class);

    public final TargetTypeRepository targetTypeRepository;
    public final MonitoredMachineInventoryRepository monitoredMachineInventoryRepository;

    @Autowired
    public MachineInventoryService(TargetTypeRepository targetTypeRepository,
                                   MonitoredMachineInventoryRepository monitoredMachineInventoryRepository) {
        this.targetTypeRepository = targetTypeRepository;
        this.monitoredMachineInventoryRepository = monitoredMachineInventoryRepository;
    }

    /**
     * machineId를 기반으로 MonitoredMachineInventory 테이블에서 해당 엔티티의 id(targetId)를 조회하여 반환한다.
     * <p>
     * 조회 결과가 없을 경우 {@link #addMachine(String, String)}을 호출하여 DB에 추가한 후,
     * 생성된 id를 반환한다.
     * </p>
     *
     * @param type      추가 또는 조회할 머신의 타입
     * @param machineId 조회 또는 추가할 머신의 고유 ID
     * @return 해당 머신의 targetId (존재하지 않으면 추가 후 생성된 id 반환)
     */
    public String changeMachineIdToTargetId(String type, String machineId) {
        // machineId를 기반으로 MonitoredMachineInventory 조회
        Optional<MonitoredMachineInventory> machineInventory =
                monitoredMachineInventoryRepository.findByMachineId(machineId);

        // 조회 결과가 있을 경우 id 반환, 없으면 addMachine 호출 후 생성된 id 반환
        return machineInventory.map(MonitoredMachineInventory::getId)
                .orElseGet(() -> {
                    addMachine(type, machineId);
                    return monitoredMachineInventoryRepository.findByMachineId(machineId)
                            .map(MonitoredMachineInventory::getId)
                            .orElse(null); // 만약 add 후에도 조회가 안 되면 null 반환
                });
    }

    /**
     *  - 새로운 머신(MonitoredMachineInventory)을 등록한다.
     *
     * <p>
     * 동작 과정:
     * <ol>
     *   <li>입력받은 type에 해당하는 TargetType 엔티티를 조회한다.</li>
     *   <li>새로운 MonitoredMachineInventory 엔티티를 생성하고, 고유 id를 생성하여 할당한다.</li>
     *   <li>machineId와 type을 엔티티에 설정한다.</li>
     *   <li>DB에 저장한다.</li>
     * </ol>
     * </p>
     *
     * @param type      등록할 머신의 타입
     * @param machine_id 등록할 머신의 고유 ID
     * @throws IllegalArgumentException type에 해당하는 TargetType이 존재하지 않을 경우 발생
     */
    public void addMachine(String type, String machine_id) {
        // 이미 DB에 저장된 TargetType을 조회
        TargetType targetType = targetTypeRepository.findByType(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + type));

        MonitoredMachineInventory machine = new MonitoredMachineInventory();
        String id = generateNextId(type);
        System.out.println("typeid(created) : "+ id);
        machine.setId(id);
        machine.setType(targetType);
        machine.setMachineId(machine_id);

        monitoredMachineInventoryRepository.save(machine);

        logger.info("머신을 성공적으로 등록하였습니다: {} -> {}, {}", id, type, machine_id);
    }

    /**
     *  - type당 고유 id를 순서대로 생성하는 메서드
     *      (ex. host001, container002)
     * @param type  머신의 타입을 구분하는 변수 (ex."host", "container")
     * @return      생성된 id를 리턴한다.
     */
    public String generateNextId(String type) {
        Pageable topOne = PageRequest.of(0, 1); // 가장 최근 하나만 가져옴

        //List<String> topIds = monitoredMachineInventoryRepository.findTopIdByType(type, topOne);
        // enum 변환
        TargetType targetType = targetTypeRepository.findByType(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + type));
        List<String> topIds = monitoredMachineInventoryRepository.findTopIdByType(targetType, topOne);

        // 관련된 ID가 하나도 없으면 001부터 시작
        if (topIds.isEmpty()) {
            return String.format("%s%03d", type, 1);
        }

        String lastId = topIds.get(0); // 예: host003
        String numberPart = lastId.substring(type.length()); // "003"
        int nextNumber = 1;

        try {
            nextNumber = Integer.parseInt(numberPart) + 1;
        } catch (NumberFormatException e) {
            logger.error("Invalid ID format found: {}. Starting from 001.", lastId);
            nextNumber = 1; // 형식이 잘못되었을 경우 기본값으로 초기화
        }
        logger.info("set Id format number: {}{}", type,nextNumber);
        return String.format("%s%03d", type, nextNumber); // host004
    }

    // 머신 모든 숫자 조회
    public int retrieveAllMachineNumber() {
        int result = 0;
        result = monitoredMachineInventoryRepository.countAll();

        return result;
    }

    // "type"을 입력한 타입의 머신 총 숫자 조회
    public int retrieveMachineNumberByType(String type) {
        int result = 0;
        result = monitoredMachineInventoryRepository.countByType(type);

        return result;
    }
}
