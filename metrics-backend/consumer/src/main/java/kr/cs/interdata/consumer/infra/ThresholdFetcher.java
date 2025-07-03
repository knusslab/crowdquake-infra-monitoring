package kr.cs.interdata.consumer.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * - Spring WebFlux 기반 비동기 클라이언트 호출 메서드
 * - 주기적으로 외부 API에서 경계값을 조회하고 이를 ThresholdStore에 업데이트하는 클래스입니다.
 *
 * - 이 클래스는 `@Scheduled` 어노테이션을 사용하여 1분마다 외부 API에서 경계값을 가져와
 * 이를 `ThresholdStore`에 저장하거나 갱신합니다. API 호출 및 응답 처리 과정은 비동기적으로 이루어집니다.
 *
 * 주요 기능:
 * - 1분마다 외부 API에서 경계값을 조회하여 `ThresholdStore`에 반영
 * - 경계값이 0 이하인 경우 경고 메시지를 로그에 기록하고, 기존 값을 유지
 * - 정상적인 값이 조회되면 해당 값을 `ThresholdStore`에 업데이트
 *
 * 이 클래스는 Spring의 `@Scheduled` 어노테이션을 사용하여 주기적인 작업을 수행하고,
 * `WebClient`를 사용해 외부 API와 비동기적으로 통신합니다.
 */
@Component
public class ThresholdFetcher {

    // 로그 변수
    private final Logger logger = LoggerFactory.getLogger(ThresholdFetcher.class);


    private final WebClient webClient;
    private final ThresholdStore thresholdStore;

    /**
     * ThresholdFetcher 생성자
     *
     * @param webClient 외부 API와의 통신을 위한 WebClient
     * @param thresholdStore 경계값을 관리하는 ThresholdStore
     */
    @Autowired
    public ThresholdFetcher(WebClient webClient, ThresholdStore thresholdStore) {
        this.webClient = webClient;
        this.thresholdStore = thresholdStore;
    }

    /**
     * 1분마다 외부 API에서 임계값을 조회하고, 이를 ThresholdStore에 업데이트하는 메서드입니다.
     *
     * @note
     * 이 메서드는 `@Scheduled` 어노테이션을 통해 1분마다 실행되며, 경계값을 조회한 후,
     * 그 값이 0 이하일 경우 경고 메시지를 출력하고 기존 값을 유지합니다.
     * 그 외의 경우에는 `thresholdStore`에 값을 업데이트합니다.
     */
    @Scheduled(fixedRate = 60000)
    public void fetchThreshold() {
        String url = "/api/metrics/threshold-check";

        webClient
                .get()      // http get 요청을 시작함
                .uri(url)   // 요청할 경로
                .retrieve() // 응답을 추출할 준비를 함.
                // 응답 바디를 비동기적으로 Mono 타입으로 변환한다. (Map<"머신 타입", Map<"메트릭 이름", "임계값">>)
                .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Double>>>() {})
                // 에러 로그
                .doOnError(error -> logger.warn("임계값 조회 실패: {}", error.getMessage()))
                // 응답으로 받은 Map
                // thresholdStore에 경계값 반영한다.
                .subscribe(response -> {
                    response.forEach((type, metrics) -> {
                        metrics.forEach((metric, value) -> {
                            if (value > 0) {
                                thresholdStore.updateThreshold(type, metric, value);

                            } else {
                                logger.warn("임계값 0 이하: {} -> {} = {} → 기존 유지", type, metric, value);
                            }
                        });
                        logger.info("type: {} -> 임계값을 조회 완료했습니다.", type);
                    });
                });

    }

}

