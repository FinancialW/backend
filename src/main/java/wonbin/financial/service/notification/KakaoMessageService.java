package wonbin.financial.service.notification;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import wonbin.financial.exception.KakaoUnauthorizedException;

/**
 * 카카오톡 "나에게 보내기" API로 메시지를 전송한다.
 * 사용자의 카카오 액세스 토큰이 필요하며(우리 JWT 아님), talk_message 동의가 선행돼야 한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoMessageService {
    private static final String MEMO_SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper;

    /**
     * @param text 텍스트 템플릿 본문(카카오 제한 200자 이내)
     * @throws KakaoUnauthorizedException 401(토큰 만료) — 호출부에서 갱신 후 1회 재시도
     */
    public void sendToMe(String accessToken, String text, String linkUrl) {
        Map<String, Object> template = Map.of(
                "object_type", "text",
                "text", text,
                "link", Map.of("web_url", linkUrl, "mobile_web_url", linkUrl),
                "button_title", "차트 보기"
        );
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("template_object", objectMapper.writeValueAsString(template));

        webClient.post()
                .uri(MEMO_SEND_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=utf-8")
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .onStatus(status -> status.value() == 401,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new KakaoUnauthorizedException()))
                .onStatus(status -> status.value() == 403,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "카카오톡 메시지 동의(talk_message)가 없습니다. 재로그인 필요: " + body)))
                .onStatus(HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("카카오 메시지 발송 실패: " + body)))
                .bodyToMono(String.class)
                .block();
    }
}
