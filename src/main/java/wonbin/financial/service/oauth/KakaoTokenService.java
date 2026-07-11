package wonbin.financial.service.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import wonbin.financial.dto.oauth.KakaoUserDto;
import wonbin.financial.dto.oauth.TokenDto;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoTokenService {
    private final WebClient webClient = WebClient.builder().build();
    @Value("${kakao.client.id}")
    private String clientId;
    @Value("${kakao.redirect-uri}")
    private String redirectUri;
    @Value("${kakao.client.secret.key}")
    private String secretKey;

    public TokenDto getAccessToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        params.add("client_secret", secretKey);

        return webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=utf-8")
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> {
                                    System.out.println("ERROR BODY: " + body);
                                    return new RuntimeException(body);
                                })
                )
                .bodyToMono(TokenDto.class)
                .block();
    }


    /** 카카오 refresh 토큰으로 access 토큰 갱신. 실패(만료/폐기) 시 4xx가 RuntimeException으로 전파된다. */
    public TokenDto refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("refresh_token", refreshToken);
        params.add("client_secret", secretKey);

        return webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=utf-8")
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> {
                                    log.error("카카오 토큰 갱신 실패: {}", body);
                                    return new RuntimeException(body);
                                })
                )
                .bodyToMono(TokenDto.class)
                .block();
    }

    public KakaoUserDto getUserInfo(String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("kapi.kakao.com")
                        .path("/v2/user/me")
                        .queryParam(
                                "property_keys",
                                "[\"kakao_account.profile.nickname\",\"kakao_account.email\"]"
                        )
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(KakaoUserDto.class)
                .block();
    }


}
