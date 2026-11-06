package com.fse.fsenotification.admin;

import com.fse.fsenotification.common.BadRequestException;

/** The current password supplied on a password change was wrong. Counted by the login rate limiter. */
public class IncorrectPasswordException extends BadRequestException {

    public IncorrectPasswordException() {
        super("Current password is incorrect");
    }
}
