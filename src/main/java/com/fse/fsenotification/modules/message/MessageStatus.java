package com.fse.fsenotification.modules.message;

public enum MessageStatus {
    /** Charged and handed to the sender. */
    PENDING,
    /** Accepted by the provider. */
    SENT,
    /** Provider rejected it; the charge was refunded. */
    FAILED
}
