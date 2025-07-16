package kr.cs.interdata.api_backend.config;

import kr.cs.interdata.api_backend.infra.websocket.MetricWebsocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@RequiredArgsConstructor
@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 웹소켓 엔드포인트: /ws/metrics
        registry.addHandler(new MetricWebsocketHandler(), "/ws/metrics")
                .setAllowedOrigins("*");
    }
}
