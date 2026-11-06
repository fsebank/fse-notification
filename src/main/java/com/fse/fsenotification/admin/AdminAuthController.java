package com.fse.fsenotification.admin;

import com.fse.fsenotification.admin.AdminAuthDtos.ChangePasswordRequest;
import com.fse.fsenotification.admin.AdminAuthDtos.LoginRequest;
import com.fse.fsenotification.admin.AdminAuthDtos.LoginResponse;
import com.fse.fsenotification.security.AdminTokenService;
import com.fse.fsenotification.security.AdminTokenService.IssuedToken;
import com.fse.fsenotification.security.LoginAttemptService;
import com.fse.fsenotification.security.Roles;
import com.fse.fsenotification.security.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exchanges admin username/password for a Bearer token used on all other /api/v1/admin/** endpoints,
 * and lets an admin change their own password. Both are throttled by {@link LoginAttemptService}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String PASSWORD_CHANGE_AUTHORITY = "ROLE_" + Roles.PASSWORD_CHANGE_REQUIRED;

    private final AuthenticationManager adminAuthenticationManager;
    private final AdminTokenService adminTokenService;
    private final AdminUserService adminUserService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping(SecurityConfig.ADMIN_LOGIN_PATH)
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.username().trim();
        String clientIp = httpRequest.getRemoteAddr();
      //  loginAttemptService.checkAllowed(username, clientIp);
        log.info("The client ip is " + clientIp);

        Authentication authentication;
        try {
            authentication = adminAuthenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(username, clientIp);
            log.warn("Failed admin login for '{}' from {}: {}", username, clientIp, ex.getClass().getSimpleName());
            throw ex;
        }
        loginAttemptService.recordSuccess(username);

        boolean passwordChangeRequired = authentication.getAuthorities().stream()
                .anyMatch(a -> PASSWORD_CHANGE_AUTHORITY.equals(a.getAuthority()));
        log.info("Admin '{}' logged in from {}{}", authentication.getName(), clientIp,
                passwordChangeRequired ? " (password change required)" : "");
        return tokenResponse(authentication.getName(), passwordChangeRequired);
    }

    /** Returns a fresh token, since tokens issued before the change are rejected from now on. */
    @PostMapping(SecurityConfig.ADMIN_CHANGE_PASSWORD_PATH)
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        Authentication authentication, HttpServletRequest httpRequest) {
        String username = authentication.getName();
        String clientIp = httpRequest.getRemoteAddr();
        loginAttemptService.checkAllowed(username, clientIp);
        try {
            adminUserService.changeOwnPassword(username, request.currentPassword(), request.newPassword());
        } catch (IncorrectPasswordException ex) {
            loginAttemptService.recordFailure(username, clientIp);
            throw ex;
        }
        return tokenResponse(username, false);
    }

    private LoginResponse tokenResponse(String username, boolean passwordChangeRequired) {
        IssuedToken token = adminTokenService.issue(username);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), username, passwordChangeRequired);
    }
}
