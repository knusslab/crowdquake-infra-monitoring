package kr.cs.interdata.localhostdatacollector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration("LocalhostDataCollectorPropertyConfig")
@PropertySources({
        @PropertySource("classpath:properties/envldc.properties") //envldc.properties 파일 소스 등록
})
public class LocalhostDataCollectorPropertyConfig {
}
