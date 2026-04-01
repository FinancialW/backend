package wonbin.financial.dto.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthResultDto {
    private final String accessToken;
    private final String refreshToken;
}
