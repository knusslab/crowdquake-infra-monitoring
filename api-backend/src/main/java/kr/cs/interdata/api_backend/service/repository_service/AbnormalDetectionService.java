package kr.cs.interdata.api_backend.service.repository_service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import kr.cs.interdata.api_backend.entity.AbnormalMetricLog;
import kr.cs.interdata.api_backend.repository.AbnormalMetricLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AbnormalDetectionService {

    private final AbnormalMetricLogRepository abnormalMetricLogRepository;
    private final Logger logger = LoggerFactory.getLogger(AbnormalDetectionService.class);

    @Autowired
    public AbnormalDetectionService(
            AbnormalMetricLogRepository abnormalMetricLogRepository){
        this.abnormalMetricLogRepository = abnormalMetricLogRepository;
    }

    /**
     *  - 이상 로그를 저장하는 메서드
     * <p>
     *  - 데이터를 {@code AbnormalMetricLog}에 저장하고, 동시에 {@code LatestAbnormalStatus}에 저장 또는 갱신한다.
     * </p>
     *
     * @param id        이상값이 발생한 머신의 고유 ID
     * @param metric    이상값이 발생한 메트릭 이름
     * @param value     이상값
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeViolation(String id, String metric, String value, LocalDateTime timestamp) {
        // 1. AbnormalMetricLog 저장
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setTargetId(id);
        abn.setMetricName(metric);
        abn.setValue(Double.valueOf(value));
        abn.setTimestamp(timestamp);
        abnormalMetricLogRepository.save(abn);
    }


    /**
     *  - 최근 이상 상태(AbnormalMetricLog)를 조회한다.
     * <p>
     *  - 지정한 날짜를 기준으로 임계값을 초과한 기록을 조회한다.
     * </p>
     *
     * @param targetDate 조회할 날짜 (yyyy-MM-dd)
     * @return 조회된 이상 기록 리스트
     */
    public List<AbnormalMetricLog> getLatestAbnormalMetricsByDate(String targetDate) {
        // 받은 날짜를 LocalDate로 변환
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 날짜의 시작 시간과 끝 시간 계산
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // DB 조회 (특정 날짜의 임계치 초과 기록만 가져옴)
        return abnormalMetricLogRepository.findByTimestampBetween(startOfDay, endOfDay);
    }

    //(선택)1달 이상 지난 로그 삭제 -> AbnrmalMetricLog

}
