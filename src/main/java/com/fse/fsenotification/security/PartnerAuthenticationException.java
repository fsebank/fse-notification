package com.fse.fsenotification.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

@Getter
public class PartnerAuthenticationException extends AuthenticationException {

    private final HttpStatus status;

    public PartnerAuthenticationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
