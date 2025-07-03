package kr.cs.interdata.consumer.config;

import kr.cs.interdata.consumer.infra.MetricWebsocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@RequiredArgsConstructor
@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    @Value("${SOCKET_ALLOWED_ADDR}")
    private String addr;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 웹소켓 엔드포인트: /ws/metrics
        registry.addHandler(new MetricWebsocketHandler(), "/ws/metrics")
                .setAllowedOrigins(addr);
    }
}
