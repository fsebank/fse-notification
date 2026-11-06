package com.fse.fsenotification.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HS256 signing key for admin access tokens, from app.security.jwt.secret (base64, at least 32 bytes).
 * Without one, a random key is generated per startup, so tokens stop working after a restart.
 */
@Slf4j
@Configuration
public class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey secretKey;

    public JwtConfig(@Value("${app.security.jwt.secret:}") String secret) {
        byte[] keyBytes;
        if (StringUtils.hasText(secret)) {
            keyBytes = Base64.getDecoder().decode(secret.trim());
            if (keyBytes.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("app.security.jwt.secret must decode to at least 32 bytes");
            }
        } else {
            keyBytes = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("app.security.jwt.secret is not set; using a random key. Admin tokens will not survive a restart");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AdminTokenService.ISSUER));
        return decoder;
    }
}
