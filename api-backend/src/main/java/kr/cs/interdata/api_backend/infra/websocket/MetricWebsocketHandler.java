package kr.cs.interdata.api_backend.infra.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricWebsocketHandler extends TextWebSocketHandler {

    // 연결된 모든 클라이언트 세션 관리
    // client가 10명 내외(많지 않다)라는 가정하에 변수를 작성하였습니다.
    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(MetricWebsocketHandler.class);

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session)  {
        try {
            sessions.put(session.getId(), session);
            // 세션 수 경고 로그
            if (sessions.size() > 100) {
                logger.warn("WebSocket session pool is too large: {}", sessions.size());
            }
            logger.info("Client Connected: {}", session.getId());
        } catch (Exception e) {
            logger.error("Failed to establish connection for session: {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status){
        try {
            WebSocketSession removedSession = sessions.remove(session.getId());
            if (removedSession != null && removedSession.isOpen()) {
                removedSession.close(); // 명시적으로 세션을 닫아줌
            }
            logger.warn("Client Disconnected: {}", session.getId());
        } catch (Exception e) {
            logger.error("Failed to remove session: {}", session.getId(), e);
        }
    }

    /**
     *  - 모든 클라이언트에게 메시지를 전송한다.
     *  - client가 10명 내외(많지 않다)라는 가정하에 메세드를 작성하였습니다.
     *
     * @param message   json 형태의 metric 데이터를 보내준다.
     */
    public void sendMetricMessage(String message) {
        // sessions: WebSocket 세션들을 저장하고 있는 Map (key: session ID, value: SseEmitter or WebSocketSession)
        sessions.forEach((id, session) -> {

            // 현재 세션이 열려있는지 확인
            if (session.isOpen()) {
                boolean sent = false;  // 전송 성공 여부를 나타내는 플래그
                int attempts = 0;      // 전송 시도 횟수

                // 최대 3번까지 재시도할 수 있도록 while 루프 실행
                while (!sent && attempts < 3) {
                    try {
                        // 클라이언트에 메시지 전송 시도
                        session.sendMessage(new TextMessage(message));

                        // 예외가 발생하지 않으면 전송 성공 → sent = true로 변경
                        sent = true;
                        // websocket 세션들을 모두 전송 완료하였음.
                        logger.info("Successfully sent message for websocket: {}", id);

                    } catch (Exception e) {
                        // 전송 실패 시 예외 발생 → attempts를 1 증가시킴
                        attempts++;
                        logger.warn("Retry sending to {} ({})", id, attempts);

                        // 3번 시도했는데도 실패하면 세션을 종료하고 Map에서 제거
                        if (attempts == 3) {
                            logger.error("Failed to send message to {} after 3 attempts", id);
                            try {
                                // 세션 종료 시도
                                session.close();
                            } catch (IOException ex) {
                                logger.error("Failed to close session for {}", id);
                            }
                            // 세션이 완전히 종료되었으므로 sessions Map에서 삭제
                            WebSocketSession removedSession = sessions.remove(id);
                            if (removedSession != null && removedSession.isOpen()) {
                                try {
                                    removedSession.close(); // 명시적으로 세션 종료
                                    logger.warn("Client session closed and removed: {}", id);
                                } catch (IOException ex) {
                                    logger.error("Failed to close session for {}", id, ex);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    @Scheduled(fixedRate = 5 * 60 * 1000) // 5분마다
    public void cleanUpDeadSessions() {
        sessions.forEach((id, session) -> {
            try {
                if (!session.isOpen()) {
                    sessions.remove(id);
                    logger.info("Cleaned up closed WebSocket session: {}", id);
                }
            } catch (Exception e) {
                logger.error("Error while checking session status: {}", id, e);
                sessions.remove(id);
            }
        });
    }
}
