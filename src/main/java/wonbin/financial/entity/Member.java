package wonbin.financial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
}
