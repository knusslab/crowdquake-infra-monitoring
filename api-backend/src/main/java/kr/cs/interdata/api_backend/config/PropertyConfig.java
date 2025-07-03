package kr.cs.interdata.api_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 *  - 외부 프로퍼티 파일(env.properties)을 애플리케이션 환경에 등록하는 설정 클래스.
 *  - 해당 설정을 통해 env.properties 파일의 값을 @Value 등으로 주입받을 수 있습니다.
 */
@Configuration
@PropertySources({
        @PropertySource("classpath:properties/env.properties") //env.properties 파일 소스 등록
})
public class PropertyConfig {
}
