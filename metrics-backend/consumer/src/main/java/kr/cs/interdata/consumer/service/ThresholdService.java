package kr.cs.interdata.consumer.service;


import kr.cs.interdata.consumer.dto.ThresholdRequest;
import kr.cs.interdata.consumer.infra.ThresholdFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

@Service
public class ThresholdService {

    // 로그 변수
    private final Logger logger = LoggerFactory.getLogger(ThresholdFetcher.class);

    private final WebClient webClient;

    @Autowired
    public ThresholdService(WebClient webClient) {
        this.webClient = webClient;  // 이미 설정된 WebClient를 주입받음
    }

    /**
     *  - 임계값 초과 데이터를 API 백엔드로 전송하는 메서드.
     *
     * @param machineId   : 메시지를 보낸 호스트/컨테이너 id
     * @param metricName   : 메트릭 이름
     * @param value    : 임계값을 넘은 값
     * @param timestamp: 임계값을 넘은 시각
     */
    public void sendThresholdViolation(String type, String machineId, String metricName, Double value, LocalDateTime timestamp) {
        ThresholdRequest request = new ThresholdRequest(type, machineId, metricName, value, timestamp);
        String url = "/api/violation-store";

        // API 백엔드로 POST 요청을 보내고 응답을 처리
        webClient.post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)  // 응답은 없으므로 처리하지 않음
                .doOnError(error -> {
                    // 에러 로그 처리
                    logger.warn("임계값 초과 전송 실패: " + error.getMessage());
                })
                .doOnTerminate(() -> {
                    // 성공적으로 요청을 마친 후의 처리
                    logger.info("임계값 초과 데이터 전송 완료: type - {}, machineId - {}, metric - {}, value - {}, timestamp - {}"
                            , type, machineId, metricName, value, timestamp);
                })
                .subscribe();  // 비동기 방식으로 호출
    }

}