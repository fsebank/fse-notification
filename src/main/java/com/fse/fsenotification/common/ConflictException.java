package com.fse.fsenotification.common;

/** Mapped to 409 Conflict. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
