package wonbin.financial.dto.oauth;

import lombok.Data;

@Data
public class TokenDto {
    private String access_token;
    private String refresh_token; // 갱신 요청 시에는 만료 임박일 때만 새로 내려옴
    private String token_type;
    private int expires_in;
    private int refresh_token_expires_in;
}
