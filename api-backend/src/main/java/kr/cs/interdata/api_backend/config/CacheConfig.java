package kr.cs.interdata.api_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kr.cs.interdata.api_backend.infra.cache.MachineMetricTimestamp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, MachineMetricTimestamp> metricTimestampCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(89))
                .maximumSize(1000)                     // 최대 캐시 크기
                .build();
    }
}
