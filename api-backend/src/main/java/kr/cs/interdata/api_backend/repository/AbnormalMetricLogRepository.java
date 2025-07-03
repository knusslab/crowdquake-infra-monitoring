package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
