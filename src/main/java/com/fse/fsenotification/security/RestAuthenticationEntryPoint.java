package com.fse.fsenotification.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (authException instanceof PartnerAuthenticationException partnerEx) {
            JsonErrorWriter.write(request, response, partnerEx.getStatus(), partnerEx.getMessage());
            return;
        }
        JsonErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
