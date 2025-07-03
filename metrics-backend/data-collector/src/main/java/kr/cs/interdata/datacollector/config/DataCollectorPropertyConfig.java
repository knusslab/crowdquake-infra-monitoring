package kr.cs.interdata.datacollector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration("DataCollectorPropertyConfig")
@PropertySources({
        @PropertySource("classpath:properties/envdc.properties") //envdc.properties 파일 소스 등록
})
public class DataCollectorPropertyConfig {
}
