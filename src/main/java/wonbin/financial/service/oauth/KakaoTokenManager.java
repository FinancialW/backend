package wonbin.financial.service.oauth;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.oauth.TokenDto;
import wonbin.financial.entity.Member;
import wonbin.financial.repository.KakaoMemberRepository;

/**
 * 회원별 카카오 access 토큰의 유효성을 관리한다.
 * 만료가 임박하면 refresh 토큰으로 갱신하고, 갱신이 불가능하면(만료/폐기) 토큰을 비워
 * 다음 로그인 전까지 메시지 발송 대상에서 제외한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoTokenManager {
    private final KakaoTokenService kakaoTokenService;
    private final KakaoMemberRepository kakaoMemberRepository;

    /** 만료 5분 전이면 미리 갱신해 발송 중 만료를 방지한다. */
    private static final int EXPIRY_MARGIN_MINUTES = 5;

    public Optional<String> getValidAccessToken(Member member) {
        if (member.getKakaoRefreshToken() == null || member.getKakaoRefreshToken().isBlank()) {
            log.info("[{}] 카카오 토큰이 없어 메시지 발송을 건너뜁니다. (재로그인 필요)", member.getKakaoId());
            return Optional.empty();
        }
        if (member.getKakaoAccessToken() != null && member.getKakaoTokenExpiresAt() != null
                && member.getKakaoTokenExpiresAt()
                .isAfter(LocalDateTime.now().plusMinutes(EXPIRY_MARGIN_MINUTES))) {
            return Optional.of(member.getKakaoAccessToken());
        }
        return refreshAndStore(member);
    }

    /** 강제 갱신(401 재시도 경로 포함). 실패 시 토큰을 초기화하고 empty를 반환한다. */
    public Optional<String> refreshAndStore(Member member) {
        try {
            TokenDto refreshed = kakaoTokenService.refreshAccessToken(member.getKakaoRefreshToken());
            member.setKakaoAccessToken(refreshed.getAccess_token());
            // 갱신 응답의 refresh_token은 만료 임박일 때만 내려오므로 없으면 기존 값 유지
            if (refreshed.getRefresh_token() != null) {
                member.setKakaoRefreshToken(refreshed.getRefresh_token());
            }
            member.setKakaoTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.getExpires_in()));
            kakaoMemberRepository.save(member);
            return Optional.of(refreshed.getAccess_token());
        } catch (Exception e) {
            log.warn("[{}] 카카오 토큰 갱신 실패, 토큰을 초기화합니다: {}", member.getKakaoId(), e.getMessage());
            member.setKakaoAccessToken(null);
            member.setKakaoRefreshToken(null);
            member.setKakaoTokenExpiresAt(null);
            kakaoMemberRepository.save(member);
            return Optional.empty();
        }
    }
}
