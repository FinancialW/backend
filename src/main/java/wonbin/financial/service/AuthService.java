package wonbin.financial.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import wonbin.financial.constant.JwtExpiration;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.dto.KakaoUserDto;
import wonbin.financial.dto.TokenDto;
import wonbin.financial.entity.Member;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final KakaoTokenService kakaoTokenService;
    private final KakaoLoginService kakaoLoginService;
    private final JwtTokenBuilder jwtTokenBuilder;

    public void saveRefreshToken(Member member, String refreshToken) {
        member.setRefreshToken(refreshToken);
    }

    public AuthResultDto kakaoLogin(String code, String state) {
        TokenDto kakaoToken = kakaoTokenService.getAccessToken(code, state);
        KakaoUserDto userInfo =
                kakaoTokenService.getUserInfo(kakaoToken.getAccess_token());
        Member member = kakaoLoginService.loginService(userInfo);
        String accessToken =
                jwtTokenBuilder.tokenCreator(member.getKakaoId(), JwtExpiration.ACCESS_TOKEN_MS.getMilliseconds());
        String refreshToken =
                jwtTokenBuilder.tokenCreator(member.getKakaoId(), JwtExpiration.REFRESH_TOKEN_DAYS.getMilliseconds());
        saveRefreshToken(member, refreshToken);
        return new AuthResultDto(accessToken, refreshToken);
    }
    public void addCookies(HttpServletResponse response, AuthResultDto result) {
        Cookie accessCookie = new Cookie("accessToken", result.getAccessToken());
        accessCookie.setHttpOnly(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(JwtExpiration.ACCESS_COOKIE.getSeconds());

        Cookie refreshCookie = new Cookie("refreshToken", result.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(JwtExpiration.REFRESH_TOKEN_DAYS.getSeconds());

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);
    }

}
