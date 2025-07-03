package kr.cs.interdata.api_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  - Jackson 관련 설정을 담당하는 클래스
 *  - 올바른 직렬화/역질렬화를 위한 JavaTimeModule을 등록한 ObjectMapper 빈을 제공한다.
 */
@Configuration
public class JacksonConfig {

    /**
     *  - 커스텀 ObjectMapper 빈을 생성하여 스프링 컨테이너에 등록한다.
     *
     * @return  JavatTimeModule이 등록된 ObjectMapper 인스턴스
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());    // 날짜/시간 타입 지우너을 위한 모듈 등록
        return mapper;
    }
}
