package wonbin.financial.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.constant.JwtExpiration;
import wonbin.financial.dto.oauth.AuthResultDto;
import wonbin.financial.dto.oauth.MemberDto;
import wonbin.financial.entity.Member;
import wonbin.financial.service.oauth.AuthService;
import wonbin.financial.service.oauth.JwtTokenBuilder;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OAuthController {
    private final AuthService authService;
    private final JwtTokenBuilder jwtTokenBuilder;
    @Value("${kakao.client.id}")
    private String kakaoClientId;
    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;
    @Value("${app.mobile-deeplink-url}")
    private String mobileDeeplinkUrl;
    @Value("${app.front-base-url}")
    private String frontBaseUrl;
    @Value("${jwt.test.id}")
    private String testId;
    @GetMapping("/auth/me")
    public ResponseEntity<MemberDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {
            throw new IllegalArgumentException("인증되지 않은 사용자");
        }
        Member member = authService.findByKakaoId(authentication.getName());
        return ResponseEntity.ok(new MemberDto(member.getMemberName(),member.getId()));
    }

    @GetMapping("/auth/reissue") // Cookie가 없는 경우 예외 상황 해결해야됨
    public ResponseEntity<AuthResultDto> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authService.extractRefreshToken(request);
        if(refreshToken==null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("RefreshToken 없음");
        }
        AuthResultDto resultDto = authService.reissue(refreshToken);
        authService.addCookies(response,resultDto);
        return ResponseEntity.ok(resultDto);
    }

    @GetMapping("/auth/kakao") // 카카오 로그인화면 이동 버튼
    public void redirectToKakao(@RequestParam(value = "client", required = false) String client,
                                HttpServletResponse response) throws IOException {
        // redirect_uri는 토큰 교환(KakaoTokenService)과 반드시 같아야 하므로 프로퍼티로 일원화
        String url = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id="+kakaoClientId
                + "&redirect_uri="+kakaoRedirectUri
                + "&response_type=code"
                // talk_message: 패턴 감지 카카오톡 알림용(개발자 콘솔 동의항목 활성화 필요)
                // account_email은 비즈 앱 인증이 필요해 제외 — 이메일은 동의 없이는 null로 온다
                + "&scope=talk_message,profile_nickname";
        // 앱(Expo)에서 시작한 로그인은 state로 표시해두면 카카오가 콜백까지 그대로 전달해준다
        if ("app".equals(client)) {
            url += "&state=app";
        }

        response.sendRedirect(url);
    }
    @GetMapping("/auth/kakao/callback")
    public void callback(@RequestParam("code") String code,
                         @RequestParam(value = "state", required = false) String state,
                         HttpServletResponse response) throws IOException {
        AuthResultDto result = authService.kakaoLogin(code);
        if ("app".equals(state)) {
            // 네이티브 앱은 쿠키를 못 쓰므로 딥링크 쿼리로 토큰을 전달한다.
            // 앱은 SecureStore에 저장 후 Authorization: Bearer 헤더로 사용(JwtAuthenticationFilter가 지원).
            String deepLink = mobileDeeplinkUrl
                    + "?accessToken=" + URLEncoder.encode(result.getAccessToken(), StandardCharsets.UTF_8)
                    + "&refreshToken=" + URLEncoder.encode(result.getRefreshToken(), StandardCharsets.UTF_8);
            response.sendRedirect(deepLink);
            return;
        }
        authService.addCookies(response,result);
        response.sendRedirect(frontBaseUrl + "/login-success");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.kakaoLogout();
        authService.expireCookie(response,"accessToken");
        authService.expireCookie(response,"refreshToken");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test/token")
    public String testToken() {
        return jwtTokenBuilder.tokenCreator(testId, JwtExpiration.REFRESH_TOKEN_DAYS.getMilliseconds());
    }
}
