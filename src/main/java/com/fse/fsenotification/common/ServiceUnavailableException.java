package com.fse.fsenotification.common;

/** Mapped to 503 Service Unavailable. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
