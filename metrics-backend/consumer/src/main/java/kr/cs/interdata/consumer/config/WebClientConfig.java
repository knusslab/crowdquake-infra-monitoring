package kr.cs.interdata.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${API_BASE_URL}")
    private String baseUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                // 기본 URL 설정
                .baseUrl(baseUrl)
                // 공통 헤더 설정
                // json형식 데이터를 요청 및 수신한다.
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.add(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE);
                })
                // WebClient 생성
                .build();
    }

}
