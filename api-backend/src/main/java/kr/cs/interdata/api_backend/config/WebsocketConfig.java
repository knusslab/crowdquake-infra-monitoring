package kr.cs.interdata.api_backend.config;

import kr.cs.interdata.api_backend.infra.websocket.MetricWebsocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 웹소켓 관련 설정을 담당하는 클래스입니다.
 */
@RequiredArgsConstructor
@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // "/ws/metrics" 엔드포인트로 들어오는 웹소켓 연결 요청을 MetricWebsocketHandler로 처리하도록 등록
        // setAllowedOrigins("*")는 모든 도메인에서의 웹소켓 연결을 허용 (내부 서비스 사용이라 허용해놓음)
        registry.addHandler(new MetricWebsocketHandler(), "/ws/metrics")
                .setAllowedOrigins("*");
    }
}
