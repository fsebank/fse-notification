package com.fse.fsenotification.modules.message.enums;

public enum MessageStatus {
    /** Charged and handed to the sender. */
    PENDING,
    /** Accepted by the provider. */
    SENT,
    /** Provider rejected it; the charge was refunded. */
    FAILED
}
