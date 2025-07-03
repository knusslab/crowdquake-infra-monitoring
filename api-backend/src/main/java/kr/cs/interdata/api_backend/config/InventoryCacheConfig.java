package kr.cs.interdata.api_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class InventoryCacheConfig {

    /**
     *  - machine Id와 machine unique id가 매칭된다.
     *  - 이를 위한 Caffeine 캐시 빈을 생성한다.
     *  - 캐시를 조회할 때,metrics를 수집하는 machine의 unique id로의 치환을 도와준다.
     *
     *  캐시 정책:
     *      - 최대 1000개의 엔트리 저장
     *      - 각 엔트리는 쓰기 이후 10분간 캐싱됨.
     *
     * @return  Caffeine Cache 인스턴스 (key: String, value: String)
     */
    @Bean
    public Cache<String, String> inventoryCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 10분 캐싱
                .maximumSize(1000)                        // 최대 1000개 캐시
                .build();
    }
}
