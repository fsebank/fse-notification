package com.fse.fsenotification.modules.message.sender;

import com.fse.fsenotification.modules.message.domain.Message;
import com.fse.fsenotification.modules.message.enums.MessageChannel;

/** Routes a message to the delivery implementation for its channel. */
public interface MessageSender {

    boolean supports(MessageChannel channel);

    /**
     * Sends to every recipient of the message.
     *
     * @throws MessageDeliveryException if the provider rejects the message; the charge is then refunded
     */
    void send(Message message);
}
