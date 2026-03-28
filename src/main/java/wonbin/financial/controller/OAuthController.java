package wonbin.financial.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.entity.Member;
import wonbin.financial.service.AuthService;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OAuthController {
    private final AuthService authService;
    @GetMapping("/auth/me")
    public Member me(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자");
        }
        return authService.findByKakaoId(authentication.getName());
    }

    @GetMapping("/auth/reissue")
    public ResponseEntity<AuthResultDto> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authService.extractRefreshToken(request);
        AuthResultDto resultDto = authService.reissue(refreshToken);
        authService.addCookies(response,resultDto);
        return ResponseEntity.ok(resultDto);
    }

    @GetMapping("/auth/kakao") // 카카오 로그인화면 이동 버튼
    public void redirectToKakao(HttpServletResponse response) throws IOException {
        String url = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=REST_API_KEY"
                + "&redirect_uri=http://localhost:8080/auth/kakao/callback"
                + "&response_type=code";

        response.sendRedirect(url);
    }
    @GetMapping("/auth/kakao/callback")
    public void callback(@RequestParam String code,
                         @RequestParam String state,
                         HttpServletResponse response) throws IOException {
        AuthResultDto result = authService.kakaoLogin(code, state);
        authService.addCookies(response,result);
        response.sendRedirect("http://localhost:5173/");
    }
}
