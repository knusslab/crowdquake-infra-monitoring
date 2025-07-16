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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

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
     *  - 이상 로그를 저장하는 메서드 1
     * <p>
     *  - 데이터를 {@code AbnormalMetricLog}에 저장한다.
     * </p>
     *
     * @param type      이상값이 발생한 머신의 type
     * @param id        이상값이 발생한 머신의 ID
     * @param name      이상값이 발생한 머신의 name
     * @param metric    이상값이 발생한 메트릭 이름
     * @param threshold 임계값
     * @param value     이상값
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeThresholdExceeded(String type,
                               String id,
                               String name,
                               String metric,
                               String threshold, String value, LocalDateTime timestamp) {
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setMessageType("thresholdExceeded");

        abn.setMachineType(type);
        abn.setMachineId(id);
        abn.setMachineName(name);
        abn.setMetricName(metric);
        abn.setThreshold(Double.valueOf(threshold));
        abn.setValue(Double.valueOf(value));
        abn.setTimestamp(timestamp);
        abnormalMetricLogRepository.save(abn);
    }

    /**
     *  - 이상 로그를 저장하는 메서드 2
     *  <p>
     *  - 데이터를 {@code AbnormalMetricLog}에 저장한다.
     *  </p>
     *
     * @param type      이상값이 발생한 머신의 type
     * @param id        이상값이 발생한 머신의 ID
     * @param name      이상값이 발생한 머신의 name
     * @param metric    이상값이 발생한 메트릭 이름
     * @param threshold 임계값
     * @param value     이상값
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeThresholdDeceeded(String type,
                                       String id,
                                       String name,
                                       String metric,
                                       String threshold, String value, LocalDateTime timestamp) {
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setMessageType("thresholdDeceeded");

        abn.setMachineType(type);
        abn.setMachineId(id);
        abn.setMachineName(name);
        abn.setMetricName(metric);
        abn.setThreshold(Double.valueOf(threshold));
        abn.setValue(Double.valueOf(value));
        abn.setTimestamp(timestamp);
        abnormalMetricLogRepository.save(abn);
    }

    /**
     *  - 이상 로그를 저장하는 메서드 3
     *  <p>
     *  - 데이터를 {@code AbnormalMetricLog}에 저장한다.
     *  </p>
     *
     * @param type      이상값이 발생한 머신의 type
     * @param id        이상값이 발생한 머신의 ID
     * @param name      이상값이 발생한 머신의 name
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeZeroValue(String type,
                               String id,
                               String name,
                               LocalDateTime timestamp) {
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setMessageType("zerovalue");

        abn.setMachineType(type);
        abn.setMachineId(id);
        abn.setMachineName(name);
        abn.setTimestamp(timestamp);
        abnormalMetricLogRepository.save(abn);
    }

    /**
     * - 이상 로그를 저장하는 메서드 4
     * <p>
     * - 데이터를 {@code AbnormalMetricLog}에 저장한다.
     * </p>
     *
     * @param type      이상값이 발생한 머신의 type
     * @param id        이상값이 발생한 머신의 ID
     * @param name      이상값이 발생한 머신의 name
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeContainerIdChanged(String type,
                                        String id,
                                        String name,
                                        LocalDateTime timestamp) {
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setMessageType("containerIdChanged");

        abn.setMachineType(type);
        abn.setMachineId(id);
        abn.setMachineName(name);
        abn.setTimestamp(timestamp);
        abnormalMetricLogRepository.save(abn);
    }

    /**
     * - 이상 로그를 저장하는 메서드 5
     * <p>
     * - 데이터를 {@code AbnormalMetricLog}에 저장한다.
     * </p>
     *
     * @param type      이상값이 발생한 머신의 type
     * @param id        이상값이 발생한 머신의 ID
     * @param name      이상값이 발생한 머신의 name
     * @param timestamp 이상값이 발생한 시각
     */
    public void storeTimeout(String type,
                             String id,
                             String name,
                             LocalDateTime timestamp) {
        AbnormalMetricLog abn = new AbnormalMetricLog();

        abn.setMessageType("timeout");

        abn.setMachineType(type);
        abn.setMachineId(id);
        abn.setMachineName(name);
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

    /**
     *  - 최근 이상 상태(AbnormalMetricLog)를 조회한다.
     * <p>
     *  - 지정한 id를 기준으로 이상 기록을 조회한다.
     * </p>
     *
     * @param targetId 조회할 targetId (ex. host001, container002, ...)
     * @return 조회된 이상 기록 리스트
     */
    public List<AbnormalMetricLog> getLatestAbnormalMetricsByMachineId(String targetId) {

        // DB 조회 (특정 날짜의 Abnormal 기록만 가져옴)
        return abnormalMetricLogRepository.findTop20BymachineIdOrderByTimestampDesc(targetId);
    }

    /**
     * - 최근 이상 상태(AbnormalMetricLog)를 조회한다.
     * <p>
     *  - 최근 날짜를 기준으로 모든 머신에서의 이상 기록을 조회한다.
     * </p>
     *
     * @return  조회한 이상 기록 리스트
     */
    public List<AbnormalMetricLog> getLatestAbnormalMetrics() {

        // DB 조회 (모든 머신에서의 Abnormal 기록 50개 가져옴)
        return abnormalMetricLogRepository.findTop50ByOrderByTimestampDesc();
    }

    /**
     *  - 매일 새벽 3시에 7일 지난 로그 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteOldAbnormalLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = abnormalMetricLogRepository.deleteByTimestampBefore(cutoff);

        logger.info("[이상로그삭제] 7일 이상된 로그 {} 건 삭제 완료", deleted);
    }


}
