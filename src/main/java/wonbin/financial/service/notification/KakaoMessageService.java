package wonbin.financial.service.notification;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
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
    private static final String IMAGE_UPLOAD_URL = "https://kapi.kakao.com/v2/api/talk/message/image/upload";

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper;

    /**
     * @param text 텍스트 템플릿 본문(카카오 제한 200자 이내)
     * @throws KakaoUnauthorizedException 401(토큰 만료) — 호출부에서 갱신 후 1회 재시도
     */
    public void sendToMe(String accessToken, String text, String linkUrl) {
        Map<String, Object> link = Map.of("web_url", linkUrl, "mobile_web_url", linkUrl);
        Map<String, Object> template = Map.of(
                "object_type", "text",
                "text", text,
                "link", link,
                "button_title", "차트 보기"
        );
        sendTemplate(accessToken, template);
    }

    /** 차트 이미지가 포함된 feed 템플릿 메시지. 이미지 URL은 외부에서 접근 가능해야 한다(uploadImage 결과 사용). */
    public void sendFeedToMe(String accessToken, String title, String description,
                             String imageUrl, String linkUrl) {
        Map<String, Object> link = Map.of("web_url", linkUrl, "mobile_web_url", linkUrl);
        Map<String, Object> template = Map.of(
                "object_type", "feed",
                "content", Map.of(
                        "title", title,
                        "description", description,
                        "image_url", imageUrl,
                        "image_width", PatternChartRenderer.WIDTH,
                        "image_height", PatternChartRenderer.HEIGHT,
                        "link", link),
                "buttons", List.of(Map.of("title", "차트 보기", "link", link))
        );
        sendTemplate(accessToken, template);
    }

    /**
     * 카카오 메시지 이미지 업로드 API(최대 5MB). 업로드된 이미지는 카카오 CDN에 일정 기간(약 3개월)
     * 보관되므로 일회성 알림에는 별도 저장소(S3 등)가 필요 없다.
     *
     * @return feed 템플릿 image_url에 그대로 쓸 수 있는 카카오 CDN URL
     */
    public String uploadImage(String accessToken, byte[] image, String filename) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        // 카카오 문서와 달리 실제 API는 multipart 필드명으로 "file"을 요구한다 ("image"는 -2 에러)
        body.part("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.IMAGE_PNG);

        String response = exchange(webClient.post()
                .uri(IMAGE_UPLOAD_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build())));

        JsonNode url = objectMapper.readTree(response).path("infos").path("original").path("url");
        if (url.isMissingNode() || url.asString().isBlank()) {
            throw new RuntimeException("카카오 이미지 업로드 응답에 URL이 없습니다: " + response);
        }
        return url.asString();
    }

    private void sendTemplate(String accessToken, Map<String, Object> template) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("template_object", objectMapper.writeValueAsString(template));
        exchange(webClient.post()
                .uri(MEMO_SEND_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=utf-8")
                .body(BodyInserters.fromFormData(params)));
    }

    /** 공통 에러 매핑: 401 → KakaoUnauthorizedException(호출부에서 토큰 갱신 후 1회 재시도). */
    private String exchange(WebClient.RequestHeadersSpec<?> request) {
        return request.retrieve()
                .onStatus(status -> status.value() == 401,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new KakaoUnauthorizedException()))
                .onStatus(status -> status.value() == 403,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "카카오톡 메시지 동의(talk_message)가 없습니다. 재로그인 필요: " + body)))
                .onStatus(HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("카카오 API 호출 실패: " + body)))
                .bodyToMono(String.class)
                .block();
    }
}
