package wonbin.financial.common.authority;

import lombok.Data;

@Data
public class TokenInfo {
    private String accessToken;
    private String grantType;

    public TokenInfo(String accessToken, String grantType) {
        this.grantType = grantType;
        this.accessToken = accessToken;
    }
}
