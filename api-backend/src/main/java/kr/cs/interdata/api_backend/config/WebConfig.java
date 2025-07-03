package kr.cs.interdata.api_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *  - 웹 애플리케이션의 CORS(Cross-Origin Resource Sharing) 정책을 설정하는 클래스
 *
 *  - 서버의 모든 엔드포인트("/**")에 대해 CORS를 허용합니다.
 *  - 허용 Origin: (React 프론트엔드 서버 주소)
 *  - 허용 HTTP 메서드: GET, POST, PUT,DELETE
 *  - 자격 증명(쿠키 등) 허용
 */
@Configuration
public class WebConfig {

    // env.properties에서 콤마로 구분된 Origin 목록을 배열로 주입받음
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     *  - CORS 설정을 위한 WebMvcConfigurer 빈을 등록한다.
     *
     * @return  CORS 정책이 적용된 WebMvcConfigurer 인스턴스
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins) // 리액트 주소
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowCredentials(true);
            }
        };
    }
}
