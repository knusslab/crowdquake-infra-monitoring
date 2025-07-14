package kr.cs.interdata.api_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kr.cs.interdata.api_backend.infra.cache.MachineMetricTimestamp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, MachineMetricTimestamp> metricTimestampCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES) // 2분 지나면 자동 제거
                .maximumSize(1000)                     // 최대 캐시 크기
                .build();
    }
}
