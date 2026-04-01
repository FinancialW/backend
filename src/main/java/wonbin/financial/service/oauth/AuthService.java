package wonbin.financial.service.oauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wonbin.financial.constant.JwtExpiration;
import wonbin.financial.dto.AuthResultDto;
import wonbin.financial.dto.KakaoUserDto;
import wonbin.financial.dto.TokenDto;
import wonbin.financial.entity.Member;
import wonbin.financial.repository.KakaoMemberRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final KakaoTokenService kakaoTokenService;
    private final KakaoLoginService kakaoLoginService;
    private final JwtTokenBuilder jwtTokenBuilder;
    private final KakaoMemberRepository kakaoMemberRepository;

    public void saveRefreshToken(Member member, String refreshToken) {
        member.setRefreshToken(refreshToken);
        kakaoMemberRepository.save(member);
    }

    public Member findByKakaoId(String kakaoId) {
        return kakaoMemberRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));
    }


    public AuthResultDto kakaoLogin(String code) {
        TokenDto kakaoToken = kakaoTokenService.getAccessToken(code);
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

    public String extractRefreshToken(HttpServletRequest request) {
        if(request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    public AuthResultDto reissue(String refreshToken) {

        Claims claims;
        try {
            claims = jwtTokenBuilder.validateAndParseToken(refreshToken).getPayload();
        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RefreshToken 만료");
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RefreshToken 위조");
        }
        String kakaoId = claims.getSubject();
        Member member = findByKakaoId(kakaoId);
        // DB와 비교
        if (!refreshToken.equals(member.getRefreshToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RefreshToken 불일치");
        }
        // 새 토큰 발급
        String newAccessToken = jwtTokenBuilder.tokenCreator(kakaoId, JwtExpiration.ACCESS_TOKEN_MS.getMilliseconds());
        String newRefreshToken = jwtTokenBuilder.tokenCreator(kakaoId, JwtExpiration.REFRESH_TOKEN_DAYS.getMilliseconds());
        // refreshToken 갱신
        member.setRefreshToken(newRefreshToken);
        kakaoMemberRepository.save(member);

        return new AuthResultDto(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void kakaoLogout() {
        String kakaoId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Member member = kakaoMemberRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));
        member.setRefreshToken(null);
    }
    public void expireCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
    }
