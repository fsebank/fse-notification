package com.fse.fsenotification.security;

/**
 * Authenticated partner, available in controllers via {@code @AuthenticationPrincipal PartnerPrincipal partner}.
 */
public record PartnerPrincipal(Long accessKeyId, String clientId, String clientName) {
}
