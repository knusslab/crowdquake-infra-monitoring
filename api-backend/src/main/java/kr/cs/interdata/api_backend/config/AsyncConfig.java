package kr.cs.interdata.api_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리를 가능하게 해주는 스프링 설정 클래스입니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 현재는 별도의 설정이 없지만, 필요하다면 Executor 빈 등을 추가하여
    // 스레드 풀 크기, 이름 등을 커스터마이징할 수 있습니다.
}