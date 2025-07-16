package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.HostMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface HostMachineInventoryRepository extends JpaRepository<HostMachineInventory, Integer> {

    /**
     *  - 모든 HostInventory 데이터의 개수를 반환한다.
     *
     * @return  전체 데이터 개수
     */
    @Query("SELECT COUNT(m) FROM HostMachineInventory m")
    int countAll();


    /**
     *  - HostInventory에 파라미터로 주어진 hostId와 hostName을 함께 가진 row 존재여부를 판별한다.
     *
     * @param hostId    판별 기준인 host Id
     * @param hostName  판별 기준인 host Name
     * @return  판별 기준을 모두 만족하면 true, 없으면 false
     */
    boolean existsByHostIdAndHostName(String hostId, String hostName);


    /**
     *  - HostName에 대응하는 row를 찾는다.
     *
     * @param hostName  판별 기준인 host Name
     * @return          판별 기준을 만족하면 해당 row를 가져온다.
     */
    Optional<HostMachineInventory> findByHostName(String hostName);
}
