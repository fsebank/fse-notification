package com.fse.fsenotification.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record CreateAdminRequest(
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[A-Za-z0-9._@-]+$") String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @Size(max = 150) String fullName) {
    }

    public record UpdateAdminStatusRequest(boolean enabled) {
    }

    /** Admin-assigned password; the target admin must change it at their next login. */
    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record AdminResponse(Long id, String username, String fullName, boolean enabled,
                                boolean mustChangePassword, Instant passwordChangedAt, String createdBy,
                                Instant createdAt, Instant updatedAt) {

        static AdminResponse from(AdminUser admin) {
            return new AdminResponse(admin.getId(), admin.getUsername(), admin.getFullName(), admin.isEnabled(),
                    admin.isMustChangePassword(), admin.getPasswordChangedAt(), admin.getCreatedBy(),
                    admin.getCreatedAt(), admin.getUpdatedAt());
        }
    }
}
