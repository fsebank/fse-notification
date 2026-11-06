package com.fse.fsenotification.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues signed access tokens for authenticated admins.
 */
@Service
public class AdminTokenService {

    public static final String ISSUER = "fse-notification";

    private final JwtEncoder jwtEncoder;
    private final Duration expiry;

    public AdminTokenService(JwtEncoder jwtEncoder,
                             @Value("${app.security.jwt.expiry:30m}") Duration expiry) {
        this.jwtEncoder = jwtEncoder;
        this.expiry = expiry;
    }

    public IssuedToken issue(String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(expiry))
                .claim("scope", Roles.ADMIN)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiry.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
