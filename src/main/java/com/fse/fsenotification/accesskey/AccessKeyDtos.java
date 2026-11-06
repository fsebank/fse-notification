package com.fse.fsenotification.accesskey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Access keys never expire; they stay valid until suspended, revoked or deleted. The IP whitelist is optional.
 */
public final class AccessKeyDtos {

    private AccessKeyDtos() {
    }

    /** Omit whitelistedIps (or send an empty set) to allow requests from any IP. */
    public record CreateAccessKeyRequest(
            @NotBlank @Size(max = 150) String clientName,
            @Size(max = 50) Set<@NotBlank @Size(max = 64) String> whitelistedIps) {
    }

    /** Null fields are left unchanged; send an empty whitelistedIps set to remove the IP restriction. */
    public record UpdateAccessKeyRequest(
            @Size(max = 150) String clientName,
            @Size(max = 50) Set<@NotBlank @Size(max = 64) String> whitelistedIps) {
    }

    public record UpdateAccessKeyStatusRequest(@NotNull AccessKeyStatus status) {
    }

    public record AccessKeyResponse(Long id, String clientName, String clientId, Set<String> whitelistedIps,
                                    AccessKeyStatus status, BigDecimal balance, Instant lastUsedAt,
                                    String createdBy, Instant createdAt, Instant updatedAt) {

        static AccessKeyResponse from(PartnerAccessKey key) {
            return new AccessKeyResponse(key.getId(), key.getClientName(), key.getClientId(),
                    Set.copyOf(key.getWhitelistedIps()), key.getStatus(), key.getBalance(), key.getLastUsedAt(),
                    key.getCreatedBy(), key.getCreatedAt(), key.getUpdatedAt());
        }
    }

    /** Returned only on creation and secret rotation: the clientSecret cannot be retrieved again. */
    public record AccessKeyCredentialsResponse(Long id, String clientName, String clientId, String clientSecret,
                                               Set<String> whitelistedIps, AccessKeyStatus status, String warning) {

        static AccessKeyCredentialsResponse from(PartnerAccessKey key, String rawSecret) {
            return new AccessKeyCredentialsResponse(key.getId(), key.getClientName(), key.getClientId(), rawSecret,
                    Set.copyOf(key.getWhitelistedIps()), key.getStatus(),
                    "Store the clientSecret securely now; it will not be shown again.");
        }
    }
}
