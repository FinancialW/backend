package wonbin.financial.constant;

import lombok.Getter;

@Getter
public enum JwtExpiration {
    ACCESS_TOKEN_MS("access", 60 * 15),
    ACCESS_COOKIE("accessCookie", 60*15),

    REFRESH_TOKEN_DAYS("refresh", 60 * 60 * 24 * 14);

    private final String name;
    private final int seconds; // 쿠키용 (int)
    private final int milliseconds; // JWT용 (long)

    JwtExpiration(String name, int seconds) {
        this.name = name;
        this.seconds = seconds;
        this.milliseconds = seconds * 1000;
    }
}
