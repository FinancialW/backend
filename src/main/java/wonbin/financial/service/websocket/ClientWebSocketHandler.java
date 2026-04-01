package wonbin.financial.service.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wonbin.financial.event.PriceUpdateEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientWebSocketHandler extends TextWebSocketHandler {
    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> activateSessions = new ConcurrentHashMap<>(); // 활성화된 세션 모두 가져옴
    private final Map<String, Set<WebSocketSession>> symbolToSessions = new ConcurrentHashMap<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        activateSessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.get("type").asString();
            if ("ENTER".equals(type)) {
                List<String> symbols = new ArrayList<>();
                for (JsonNode symbolNode : jsonNode.get("symbols")) {
                    String symbol = symbolNode.asString();
                    symbols.add(symbol);
                    symbolToSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);
                }
                subscriptionManager.onUserLogin(session.getId(), symbols);
                log.info("연결 성공, 세션 ID : {}", session.getId());

            } else if ("ADD".equals(type)) {
                String symbol = jsonNode.get("symbol").asString();
                subscriptionManager.addSymbol(session.getId(), symbol);
                symbolToSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);

            } else if ("REMOVE".equals(type)) {
                String symbol = jsonNode.get("symbol").asString();
                subscriptionManager.removeSymbol(session.getId(), symbol);
                if (symbolToSessions.containsKey(symbol)) {
                    symbolToSessions.get(symbol).remove(session);
                }
            }
        } catch (Exception e) {
            // 프론트엔드에서 이상한 값을 보내더라도 세션이 끊어지지 않도록 방어
            log.error("클라이언트 메시지 처리 중 오류 발생: {}", e.getMessage());
        }
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            activateSessions.remove(session.getId());
            subscriptionManager.onUserLogout(session.getId());
            symbolToSessions.values().forEach(sessions -> sessions.remove(session));
            log.info("연결 종료, 세션 ID : {}", session.getId());
        } catch (Exception e) {
            log.error("세션 종료 중 오류 : {}", e.getMessage());
        }
    }

    @EventListener
    public void handlePriceUpdate(PriceUpdateEvent event) {
        String symbol = event.getSymbol();
        log.info("FINNHUB DATA:{}", symbol);
        Set<WebSocketSession> sessions = symbolToSessions.get(symbol);

        if (sessions != null && !sessions.isEmpty()) {
            String payload = String.format(
                    "{\"type\":\"PRICE\",\"symbol\":\"%s\",\"price\":%s}",
                    symbol, event.getPrice()
            );
            TextMessage textMessage = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (Exception e) {
                        log.error(" 전송 에러: {}", e.getMessage());
                    }
                }
            }
        }
    }
}
