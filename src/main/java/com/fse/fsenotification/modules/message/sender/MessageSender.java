package com.fse.fsenotification.modules.message.sender;

import com.fse.fsenotification.modules.message.domain.Message;
import com.fse.fsenotification.modules.message.enums.MessageChannel;

/**
 * Delivers a message through a provider. Implement one per channel (e.g. Termii/Infobip for SMS, SMTP/SES for
 * email) and register it as a bean; it replaces the log-only placeholder for that channel.
 */
public interface MessageSender {

    MessageChannel channel();

    /**
     * Sends to every recipient of the message.
     *
     * @throws MessageDeliveryException if the provider rejects the message; the charge is then refunded
     */
    void send(Message message);
}
