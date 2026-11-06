package com.fse.fsenotification.security;

import com.fse.fsenotification.admin.AdminUserDetails;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Re-loads the admin on every request so that disabling an account or changing its password takes effect
 * immediately, rather than when the token expires. Authorities come from the database, not the token.
 */
class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserDetailsService adminUserDetailsService;

    AdminJwtAuthenticationConverter(UserDetailsService adminUserDetailsService) {
        this.adminUserDetailsService = adminUserDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserDetails admin;
        try {
            admin = adminUserDetailsService.loadUserByUsername(jwt.getSubject());
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Invalid token");
        }
        if (!admin.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        if (admin instanceof AdminUserDetails details && issuedBeforePasswordChange(jwt, details.getPasswordChangedAt())) {
            throw new BadCredentialsException("Token was issued before the last password change");
        }
        return new JwtAuthenticationToken(jwt, admin.getAuthorities(), admin.getUsername());
    }

    /** JWT iat has whole-second precision, so compare at that resolution. */
    private static boolean issuedBeforePasswordChange(Jwt jwt, Instant passwordChangedAt) {
        return passwordChangedAt != null && jwt.getIssuedAt() != null
                && jwt.getIssuedAt().isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS));
    }
}
