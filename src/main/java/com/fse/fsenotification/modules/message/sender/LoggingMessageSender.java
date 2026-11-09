package com.fse.fsenotification.modules.message.sender;

import com.fse.fsenotification.modules.message.domain.Message;
import com.fse.fsenotification.modules.message.enums.MessageChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback for channels without a configured delivery implementation. It must fail so undelivered
 * messages are not reported as sent or charged.
 */
@Slf4j
public class LoggingMessageSender implements MessageSender {

    private final MessageChannel channel;

    public LoggingMessageSender(MessageChannel channel) {
        this.channel = channel;
    }

    @Override
    public boolean supports(MessageChannel requestedChannel) {
        return channel == requestedChannel;
    }

    @Override
    public void send(Message message) {
        log.warn("No delivery provider configured for {} message {}", channel, message.getId());
        throw new MessageDeliveryException("No delivery provider configured for " + channel);
    }
}
