package wonbin.financial.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.constant.JwtExpiration;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.dto.MemberDto;
import wonbin.financial.entity.Member;
import wonbin.financial.service.AuthService;
import wonbin.financial.service.JwtTokenBuilder;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OAuthController {
    private final AuthService authService;
    private final JwtTokenBuilder jwtTokenBuilder;
    @Value("${kakao.client.id}")
    private String kakaoClientId;
    @Value("${jwt.test.id}")
    private String testId;
    @GetMapping("/auth/me")
    public ResponseEntity<MemberDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Member byKakaoId = authService.findByKakaoId(authentication.getName());
            return ResponseEntity.ok(new MemberDto(byKakaoId.getMemberName(), byKakaoId.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/auth/reissue") // Cookie가 없는 경우 예외 상황 해결해야됨
    public ResponseEntity<AuthResultDto> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authService.extractRefreshToken(request);
        AuthResultDto resultDto = authService.reissue(refreshToken);
        authService.addCookies(response,resultDto);
        return ResponseEntity.ok(resultDto);
    }

    @GetMapping("/auth/kakao") // 카카오 로그인화면 이동 버튼
    public void redirectToKakao(HttpServletResponse response) throws IOException {
        String url = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id="+kakaoClientId
                + "&redirect_uri=http://192.168.0.33:8080/auth/kakao/callback"
                + "&response_type=code";

        response.sendRedirect(url);
    }
    @GetMapping("/auth/kakao/callback")
    public void callback(@RequestParam("code") String code,
                         HttpServletResponse response) throws IOException {
        AuthResultDto result = authService.kakaoLogin(code);
        authService.addCookies(response,result);
        response.sendRedirect("http://192.168.0.33:5173/login-success");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        try {
            authService.kakaoLogout();
        } catch (Exception e) {
            System.out.println("로그아웃 로직 예외 무시: " + e.getMessage());
        } finally {
            authService.expireCookie(response, "accessToken");
            authService.expireCookie(response, "refreshToken");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test/token")
    public String testToken() {
        return jwtTokenBuilder.tokenCreator(testId, JwtExpiration.REFRESH_TOKEN_DAYS.getMilliseconds());
    }
}
