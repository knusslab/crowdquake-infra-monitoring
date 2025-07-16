package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.ContainerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContainerInventoryRepository extends JpaRepository<ContainerInventory, Integer> {

    /**
     *  - 모든 ContainerInventory 데이터의 개수를 반환한다.
     *
     * @return  전체 데이터 개수
     */
    @Query("SELECT COUNT(m) FROM ContainerInventory m")
    int countAll();

    /**
     *  - ContainerInventory에 파라미터로 주어진 hostName과 containerId, containerName을 함께 가진 row 존재여부를 판별한다.
     *
     * @param hostName  container가 종속된 host 이름
     * @param containerId   container id
     * @param containerName container name
     * @return  해당 조건들을 모두 만족하는 row가 있으면 true, 없으면 false
     */
    boolean existsByHostNameAndContainerIdAndContainerName(String hostName, String containerId, String containerName);

    /**
     *  - 파라미터로 주어진 containerId와 containerName 조합을 가진 row가 있다면, 그 row의 hostName을 반환한다.
     *
     * @param containerId       파싱할 container id
     * @param containerName     파싱할 container name
     * @return  해당 조건들을 모두 만족하는 row가 있으면 해당 row의 hostName을 반환
     */
    @Query("SELECT c.hostName FROM ContainerInventory c WHERE c.containerId = :containerId AND c.containerName = :containerName")
    String findHostNameByContainerIdAndContainerName(@Param("containerId") String containerId,
                                                               @Param("containerName") String containerName);


    Optional<ContainerInventory> findByHostNameAndContainerName(String hostName, String containerName);
}
