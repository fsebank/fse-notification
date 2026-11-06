package com.fse.fsenotification.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String PASSWORD_CHANGE_AUTHORITY = "ROLE_" + Roles.PASSWORD_CHANGE_REQUIRED;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean passwordChangeRequired = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> PASSWORD_CHANGE_AUTHORITY.equals(a.getAuthority()));
        String message = passwordChangeRequired
                ? "Password change required; call POST " + SecurityConfig.ADMIN_CHANGE_PASSWORD_PATH
                : "Access denied";
        JsonErrorWriter.write(request, response, HttpStatus.FORBIDDEN, message);
    }
}
