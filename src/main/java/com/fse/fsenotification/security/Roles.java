package com.fse.fsenotification.security;

public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String PARTNER = "PARTNER";
    /** Admin whose password must be changed before any other admin API can be used. */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private Roles() {
    }
}
