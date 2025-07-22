package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AbnormalMetricLogRepository extends JpaRepository<AbnormalMetricLog, Integer> {

    /**
     *  - 주어진 시작 시간(start)과 종료 시간(end) 사이에 발생한 임계값 초과(AbnormalMetricLog) 기록을 조회한다.
     *
     * @param start 조회 시작 시간 (포함)
     * @param end   조회 종료 시간 (포함)
     * @return 해당 기간에 발생한 AbnormalMetricLog 리스트
     */
    List<AbnormalMetricLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    /**
     *  - 주어진 machine ID로 필터링하여 가장 최근을 기준으로 최대 20개의 로그들을 리스트로 저장해 반환한다.
     *
     * @param targetId  필터링할 machine ID
     * @return  machineId = machineId인 최근 로그들 중 최대 20개를 저장한 리스트
     */
    List<AbnormalMetricLog> findTop20ByMachineIdOrderByTimestampDesc(String targetId);

    List<AbnormalMetricLog> findTop50ByMachineTypeOrderByTimestampDesc(String machineType);

    List<AbnormalMetricLog> findTop50ByMachineNameOrderByTimestampDesc(String machineName);

    List<AbnormalMetricLog> findTop50ByMessageTypeOrderByTimestampDesc(String messageType);

    List<AbnormalMetricLog> findTop50ByMetricNameOrderByTimestampDesc(String metricName);


    @Query("SELECT l FROM AbnormalMetricLog l " +
            "WHERE (:start IS NULL OR l.timestamp >= :start) " +
            "AND (:end IS NULL OR l.timestamp <= :end) " +
            "AND (:machineType IS NULL OR l.machineType = :machineType) " +
            "AND (:hostName IS NULL OR l.hostName = :hostName) " +
            "AND (:machineName IS NULL OR l.machineName = :machineName) " +
            "AND (:messageType IS NULL OR l.messageType = :messageType) " +
            "AND (:metricName IS NULL OR l.metricName = :metricName) " +
            "ORDER BY l.timestamp DESC")
    List<AbnormalMetricLog> findFilteredLogs(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("machineType") String machineType,
            @Param("hostName") String hostName,
            @Param("machineName") String machineName,
            @Param("messageType") String messageType,
            @Param("metricName") String metricName
    );

    @Query("SELECT a FROM AbnormalMetricLog a " +
            "WHERE a.machineType = 'host' AND a.machineName = :machineName " +
            "ORDER BY a.timestamp DESC")
    List<AbnormalMetricLog> findTop50HostLogsByMachineName(@Param("machineName") String machineName);

    /**
     *  - host machine과 container의 모든 머신에서 가장 최근을 기준으로 최대 50개의 로그들을 리스트로 저장해 반환한다.
     * 
     * @return  최근 모든 로그들 중 최대 50개를 저장한 리스트
     */
    List<AbnormalMetricLog> findTop50ByOrderByTimestampDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM AbnormalMetricLog l WHERE l.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
}
