package com.fse.fsenotification.modules.message.sender;

public class MessageDeliveryException extends RuntimeException {

    public MessageDeliveryException(String message) {
        super(message);
    }

    public MessageDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
