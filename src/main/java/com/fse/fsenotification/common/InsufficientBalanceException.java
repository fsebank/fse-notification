package com.fse.fsenotification.common;

/** Mapped to 402 Payment Required. */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
