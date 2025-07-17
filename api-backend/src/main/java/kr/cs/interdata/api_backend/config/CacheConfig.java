package kr.cs.interdata.api_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kr.cs.interdata.api_backend.infra.cache.MachineMetricTimestamp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine 기반 캐시 설정 클래스입니다.
 */
@Configuration
public class CacheConfig {

    /**
     * metricTimestampCache 이름의 Caffeine 캐시 빈을 등록합니다.
     * 이 캐시는 String을 키로 하고, MachineMetricTimestamp를 값으로 가집니다.
     * - 캐시 항목은 89초 후에 만료(expireAfterWrite)됩니다.
     * - 최대 1000개의 항목만 저장되며 이를 초과하면 가장 오래된 항목부터 삭제됩니다.
     *
     * @return 생성된 캐시 인스턴스
     */
    @Bean
    public Cache<String, MachineMetricTimestamp> metricTimestampCache() {
        return Caffeine.newBuilder()
                // 항목이 쓰여진(등록/갱신된) 후 89초가 지나면 캐시에서 제거됩니다.
                .expireAfterWrite(Duration.ofSeconds(89))
                // 캐시에 최대 1000개의 엔트리만 저장됩니다.
                .maximumSize(1000)    // 최대 캐시 크기
                .build();
    }
}
