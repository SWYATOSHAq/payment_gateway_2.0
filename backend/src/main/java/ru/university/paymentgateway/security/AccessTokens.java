package ru.university.paymentgateway.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

@Component
public class AccessTokens {
    public static final Duration LIFETIME = Duration.ofMinutes(15);
    public static final String ISSUER = "payment-gateway";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JwtEncoder encoder;

    public AccessTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(UUID user_id, UUID session_id) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(user_id.toString())
                .audience(List.of(ISSUER)).issuedAt(now).expiresAt(now.plus(LIFETIME))
                .claim("sid", session_id.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public static byte[] random_bytes() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String new_refresh_token() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random_bytes());
    }
}
