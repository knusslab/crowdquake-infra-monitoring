package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.MonitoredMachineInventory;
import kr.cs.interdata.api_backend.entity.TargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonitoredMachineInventoryRepository extends JpaRepository<MonitoredMachineInventory, String> {

    /**
     *  - 주어진 타입(typeName)에 해당하는 모든 MonitoredMachineInventory 데이터의 개수를 반환한다.
     *
     * @param typeName 조회할 타입의 이름
     * @return 해당 타입의 데이터 개수
     */
    @Query("SELECT COUNT(m) FROM MonitoredMachineInventory m WHERE m.type.type = :typeName")
    int countByType(@Param("typeName") String typeName);

    /**
     *  - 모든 MonitoredMachineInventory 데이터의 개수를 반환한다.
     *
     * @return  전체 데이터 개수
     */
    @Query("SELECT COUNT(m) FROM MonitoredMachineInventory m")
    int countAll();

    /**
     *  - 주어진 타입에 해당하는 MonitoredMachineInventory 중 displayId(id)의 최대값을 반환한다.
     *
     * @param type      조회할 타입
     * @param pageable  페이징 정보 (최상위 1개 추출 등)
     * @return  id의 내림파순으로 정렬된 결과 리스트
     */
    @Query("SELECT m.id " +
            "FROM MonitoredMachineInventory m " +
            "WHERE m.type = :type AND m.id IS NOT NULL " +
            "ORDER BY m.id DESC")
    List<String> findTopIdByType(@Param("type") TargetType type, Pageable pageable);

    /**
     *  - 주어진 machineId에 해당하는 MonitoredMachineInventory 엔티티를 조회힌다.
     *
     * @param machineId 조회할 머신의 ID
     * @return  해당 machineId의 MonitoredMachineInventory, 없으면 Optional.empty()
     */
    Optional<MonitoredMachineInventory> findByMachineId(String machineId);

}
