package wonbin.financial.service.oauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenBuilder {
    private final SecretKey key;
    public JwtTokenBuilder(@Value("${jwt.secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String tokenCreator(String userId, long expireMs) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime()+expireMs);
        return Jwts.builder()
                .subject(userId)
                .expiration(expireDate)
                .issuedAt(now)
                .signWith(key)
                .compact();
    }

    public Jws<Claims> validateAndParseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public String getUserId(String token) {
        return validateAndParseToken(token).getPayload().getSubject();
    }
}
