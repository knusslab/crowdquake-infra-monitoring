package kr.cs.interdata.consumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration("ConsumerPropertyConfig")
@PropertySources({
        @PropertySource("classpath:properties/envfile.properties") //envfile.properties 파일 소스 등록
})
public class ConsumerPropertyConfig {
}
