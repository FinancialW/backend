package wonbin.financial.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.service.AuthService;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OAuthController {
    private final AuthService authService;
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
