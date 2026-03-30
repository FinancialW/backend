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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.dto.MemberDto;
import wonbin.financial.entity.Member;
import wonbin.financial.service.AuthService;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OAuthController {
    private final AuthService authService;
    @Value("${kakao.client.id}")
    private String kakaoClientId;
    @GetMapping("/auth/me")
    public MemberDto me(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자");
        }
        Member byKakaoId = authService.findByKakaoId(authentication.getName());
        return new MemberDto(byKakaoId.getMemberName(), byKakaoId.getId());
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
                + "?client_id="+kakaoClientId
                + "&redirect_uri=http://localhost:8080/auth/kakao/callback"
                + "&response_type=code";

        response.sendRedirect(url);
    }
    @GetMapping("/auth/kakao/callback")
    public void callback(@RequestParam("code") String code,
                         HttpServletResponse response) throws IOException {
        AuthResultDto result = authService.kakaoLogin(code);
        authService.addCookies(response,result);
        response.sendRedirect("http://localhost:5173/");
    }
}
