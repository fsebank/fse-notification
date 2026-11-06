package com.fse.fsenotification.accesskey;

public enum BalanceTransactionType {
    /** Admin top-up. */
    CREDIT,
    /** Charge for a message. */
    DEBIT,
    /** Charge returned because a message failed to send. */
    REFUND
}
