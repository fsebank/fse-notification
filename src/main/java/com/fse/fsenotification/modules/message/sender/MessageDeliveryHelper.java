package com.fse.fsenotification.modules.message.sender;

import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;
import com.fse.fsenotification.integration.email.messaging.service.EmailMessageService;
import com.fse.fsenotification.integration.sms.messaging.service.MessageService;
import com.fse.fsenotification.modules.message.domain.Message;
import com.fse.fsenotification.modules.message.enums.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** Routes messages to the provider selected for their channel. */
@Component
@Slf4j
public class MessageDeliveryHelper implements MessageSender {
    private final Map<String, MessageService> smsProviders;
    private final Map<String, EmailMessageService> emailProviders;
    private final String smsProviderName;
    private final String emailProviderName;
    private final boolean emailEnabled;

    public MessageDeliveryHelper(Map<String, MessageService> smsProviders,
                                 Map<String, EmailMessageService> emailProviders,
                                 @Value("${sms.provider:AFRICASTALKINGSMS}") String smsProviderName,
                                 @Value("${email.provider:SMTP}") String emailProviderName,
                                 @Value("${app.notification.email.enabled:false}") boolean emailEnabled) {
        this.smsProviders = smsProviders;
        this.emailProviders = emailProviders;
        this.smsProviderName = smsProviderName;
        this.emailProviderName = emailProviderName;
        this.emailEnabled = emailEnabled;
    }

    @Override
    public boolean supports(MessageChannel channel) {
        return channel == MessageChannel.SMS || channel == MessageChannel.EMAIL;
    }

    @Override
    public void send(Message message) {
        switch (message.getChannel()) {
            case SMS -> sendSms(message);
            case EMAIL -> sendEmail(message);
        }
    }

    private void sendSms(Message message) {
        MessageService provider = smsProviders.get(smsProviderName);
        if (provider == null) {
            throw new MessageDeliveryException("SMS provider '%s' is not configured. Available providers: %s"
                    .formatted(smsProviderName, smsProviders.keySet()));
        }

        SendSMSRequestDto request = SendSMSRequestDto.builder()
                .message(message.getBody())
                .sender(message.getSenderId())
                .recipient(String.join(",", message.getRecipientList()))
                .build();
        SendSMSResponseDto response = provider.sendSMS(request);
        if (response == null || !StringUtils.hasText(response.getStatus())
                || !"success".equalsIgnoreCase(response.getStatus())) {
            String status = response == null ? "no response" : response.getStatus();
            throw new MessageDeliveryException("%s failed to send SMS: %s".formatted(smsProviderName, status));
        }

        log.info("SMS message {} accepted by {} for {} recipient(s)", message.getId(), smsProviderName,
                message.getRecipientCount());
    }

    private void sendEmail(Message message) {
        if (!emailEnabled) {
            throw new MessageDeliveryException("Email delivery is disabled; set EMAIL_ENABLED=true to enable it");
        }
        EmailMessageService provider = emailProviders.get(emailProviderName);
        if (provider == null) {
            throw new MessageDeliveryException("Email provider '%s' is not configured. Available providers: %s"
                    .formatted(emailProviderName, emailProviders.keySet()));
        }

        provider.sendEmail(message.getRecipientList(), message.getSubject(), message.getBody(),
                message.getAttachments());
        log.info("Email message {} accepted by {} for {} recipient(s)", message.getId(), emailProviderName,
                message.getRecipientCount());
    }
}
