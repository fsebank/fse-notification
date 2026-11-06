package com.fse.fsenotification.admin;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collection;

/**
 * Admin principal carrying the password-change timestamp, so tokens issued before a password change can be rejected.
 */
@Getter
public class AdminUserDetails extends User {

    private final Instant passwordChangedAt;

    public AdminUserDetails(String username, String passwordHash, boolean enabled,
                            Collection<? extends GrantedAuthority> authorities, Instant passwordChangedAt) {
        super(username, passwordHash, enabled, true, true, true, authorities);
        this.passwordChangedAt = passwordChangedAt;
    }
}
