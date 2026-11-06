package com.fse.fsenotification.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminAuthDtos {

    private AdminAuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    /** When passwordChangeRequired is true the token only works for the change-password endpoint. */
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, String username,
                                boolean passwordChangeRequired) {
    }
}
