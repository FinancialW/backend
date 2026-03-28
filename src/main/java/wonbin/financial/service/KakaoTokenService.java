package wonbin.financial.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import wonbin.financial.dto.KakaoUserDto;
import wonbin.financial.dto.TokenDto;

@Service
@RequiredArgsConstructor
public class KakaoTokenService {
    private final WebClient webClient = WebClient.builder().build();
    @Value("${kakao.client.id}")
    private String clientId;
    @Value("${kakao.client.secret.key}")
    private String secretKey;
    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    public TokenDto getAccessToken(String code, String state) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("kauth.kakao.com")
                        .path("/oauth/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", secretKey)
                        .queryParam("code", code)
                        .queryParam("state", state)
                        .queryParam("redirect_uri", redirectUri)
                        .build()
                )
                .retrieve()
                .bodyToMono(TokenDto.class)
                .block(); // 동기 처리
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
