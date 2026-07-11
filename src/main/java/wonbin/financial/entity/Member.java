package wonbin.financial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
public class Member {
    @GeneratedValue
    @Id
    private Long id;

    @Column(name = "member_name")
    private String memberName;

    private String email;
    @Column(name = "kakao_id")
    private String kakaoId;
    private String refreshToken;

    // 카카오톡 메시지 발송용 카카오 토큰(재로그인 전 사용자는 null)
    @Column(name = "kakao_access_token", length = 512)
    private String kakaoAccessToken;

    @Column(name = "kakao_refresh_token", length = 512)
    private String kakaoRefreshToken;

    @Column(name = "kakao_token_expires_at")
    private LocalDateTime kakaoTokenExpiresAt;
}
