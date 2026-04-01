package wonbin.financial.service.websocket;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import wonbin.financial.dto.finnhubDto.FinnhubResponseDto;
import wonbin.financial.dto.finnhubDto.FinnhubResponseDto.TradeData;
import wonbin.financial.event.FinnhubConnectedEvent;
import wonbin.financial.event.PriceUpdateEvent;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinnhubWebSocketClient {

    private final ApplicationEventPublisher eventPublisher; // 이벤트 발행기
    private final ObjectMapper objectMapper;
    @Value("${FINNHUB_WS_URL}")
    private String FINNHUB_WS_URL;
    private WebSocketSession webSocketSession; // 외부에서 메시지를 보낼 수 있는 세션 저장

    @PostConstruct
    public void connect() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();

            client.execute(new TextWebSocketHandler() {

                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("Finhub 연결 성공");
                    webSocketSession = session;
                    eventPublisher.publishEvent(new FinnhubConnectedEvent());

                }

                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    try {
                        String payload = message.getPayload();
                        log.info("FINNHUB DATA: {}", payload);
                        FinnhubResponseDto finnhubResponseDto = objectMapper.readValue(payload,
                                FinnhubResponseDto.class);
                        if("trade".equals(finnhubResponseDto.getType()) && finnhubResponseDto.getData() != null) {
                            TradeData lastTrade = finnhubResponseDto.getData().get(finnhubResponseDto.getData().size()-1);
                            eventPublisher.publishEvent(new PriceUpdateEvent(lastTrade.getS(), lastTrade.getP()));
                        }
                    } catch (Exception e) {
                        log.error("메시지 파싱 에러: {}", e.getMessage());
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) {
                    log.info("에러:{}", exception.getMessage());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                    log.info("연결 종료");
                }
            }, FINNHUB_WS_URL);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String symbol) throws Exception {
        if(webSocketSession!=null && webSocketSession.isOpen()) {
            webSocketSession.sendMessage(new TextMessage(
                    String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol)
            ));
        }
        System.out.println("subscribe: " + symbol);
    }

    public void unsubscribe(String symbol) throws Exception {
        if(webSocketSession!=null && webSocketSession.isOpen()) {
            webSocketSession.sendMessage(new TextMessage(
                    String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol)
            ));
        }
        System.out.println("unsubscribe: " + symbol);
    }
}