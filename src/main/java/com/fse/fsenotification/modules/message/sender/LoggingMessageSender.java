package com.fse.fsenotification.modules.message.sender;

import com.fse.fsenotification.modules.message.Message;
import com.fse.fsenotification.modules.message.MessageChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder that only logs. Used for a channel until a real {@link MessageSender} bean for it is registered;
 * messages are marked SENT and charged, but nothing leaves the system.
 */
@Slf4j
public class LoggingMessageSender implements MessageSender {

    private final MessageChannel channel;

    public LoggingMessageSender(MessageChannel channel) {
        this.channel = channel;
    }

    @Override
    public MessageChannel channel() {
        return channel;
    }

    @Override
    public void send(Message message) {
        log.info("[{} placeholder - not delivered] message {} to {} recipient(s): {}", channel, message.getId(),
                message.getRecipientCount(), message.getRecipientList());
    }
}
