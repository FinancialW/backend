package wonbin.financial.service.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.oauth.KakaoUserDto;
import wonbin.financial.entity.Member;
import wonbin.financial.repository.KakaoMemberRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoLoginService {
    private final KakaoMemberRepository kakaoMemberRepository;
    public Member loginService(KakaoUserDto dto) {
        String kakaoId = String.valueOf(dto.getId());
        return kakaoMemberRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    Member newMember = new Member();
                    newMember.setMemberName(dto.getKakao_account().getProfile().getNickname());
                    newMember.setKakaoId(dto.getId());
                    newMember.setEmail(dto.getKakao_account().getEmail());
                    log.info("새로운 회원이어서, 회원가입 진행");
                    return kakaoMemberRepository.save(newMember);
                });
    }
}
