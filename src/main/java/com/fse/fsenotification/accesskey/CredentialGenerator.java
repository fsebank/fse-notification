package com.fse.fsenotification.accesskey;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates client ids / secrets and hashes secrets for storage.
 * <p>
 * Secrets are 256-bit random values, so a fast SHA-256 digest is sufficient (no brute-force risk as with
 * human passwords) and keeps per-request validation cheap compared to BCrypt.
 */
@Component
public class CredentialGenerator {

    private static final String CLIENT_ID_PREFIX = "fse_";
    private static final String CLIENT_SECRET_PREFIX = "fsk_";

    private final SecureRandom secureRandom = new SecureRandom();

    public String newClientId() {
        return CLIENT_ID_PREFIX + HexFormat.of().formatHex(randomBytes(12));
    }

    public String newClientSecret() {
        return CLIENT_SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }

    public String hashSecret(String rawSecret) {
        return HexFormat.of().formatHex(sha256(rawSecret));
    }

    /** Constant-time comparison of a presented raw secret against a stored hash. */
    public boolean matches(String rawSecret, String storedHash) {
        byte[] presented = sha256(rawSecret);
        byte[] stored;
        try {
            stored = HexFormat.of().parseHex(storedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(presented, stored);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
