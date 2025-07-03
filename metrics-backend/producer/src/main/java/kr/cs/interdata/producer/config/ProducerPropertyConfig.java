package kr.cs.interdata.producer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration("ProducerPropertyConfig")
@PropertySources({
        @PropertySource("classpath:properties/envp.properties") //envp.properties 파일 소스 등록
})
public class ProducerPropertyConfig {
}
