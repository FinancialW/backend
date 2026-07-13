package wonbin.financial.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import wonbin.financial.service.websocket.ClientWebSocketHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClientWebSocketHandler clientWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 네이티브 앱(Expo)은 Origin 헤더를 보내지 않아 목록과 무관하게 허용된다
        registry.addHandler(clientWebSocketHandler, "/ws")
                .setAllowedOrigins(
                        "https://app.financialw.kro.kr",
                        "https://www.financialw.kro.kr",
                        "https://financialwfe.vercel.app",
                        "http://localhost:5173",
                        "http://192.168.0.33:5173",
                        "http://10.0.2.2:5173"
                );
    }
}