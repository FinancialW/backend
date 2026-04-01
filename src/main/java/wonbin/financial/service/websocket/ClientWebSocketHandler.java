package wonbin.financial.service.websocket;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientWebSocketHandler extends TextWebSocketHandler {
    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        JsonNode jsonNode = objectMapper.readTree(payload);
        String type = jsonNode.get("type").asString();

        if("ENTER".equals(type)) {
            List<String> symbols = new ArrayList<>();
            for(JsonNode symbolNode : jsonNode.get("symbols")) {
                symbols.add(symbolNode.asString());
            }
            subscriptionManager.onUserLogin(session.getId(), symbols);
            log.info("연결 성공, 세션 ID : {}", session.getId());
        } else if("ADD".equals(type)) {
            String symbol = jsonNode.get("symbol").asString();
            subscriptionManager.addSymbol(session.getId(), symbol);
        } else if("REMOVE".equals(type)) {
            String symbol = jsonNode.get("symbol").asString();
            subscriptionManager.removeSymbol(session.getId(), symbol);
        }
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptionManager.onUserLogout(session.getId());
        log.info("연결 종료, 세션 ID : {}", session.getId());
    }
}
