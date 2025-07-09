package kr.cs.interdata.api_backend.service.repository_service;

import kr.cs.interdata.api_backend.repository.MonitoredMachineInventoryRepository;
import kr.cs.interdata.api_backend.repository.TargetTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class MachineInventoryService {

    public final TargetTypeRepository targetTypeRepository;
    public final MonitoredMachineInventoryRepository monitoredMachineInventoryRepository;

    @Autowired
    public MachineInventoryService(TargetTypeRepository targetTypeRepository,
                                   MonitoredMachineInventoryRepository monitoredMachineInventoryRepository) {
        this.targetTypeRepository = targetTypeRepository;
        this.monitoredMachineInventoryRepository = monitoredMachineInventoryRepository;
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
