package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service("SMTP")
@ConditionalOnProperty(name = "email.provider", havingValue = "SMTP", matchIfMissing = true)
public class SmtpEmailMessageService implements EmailMessageService {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String host;
    private final String fromAddress;

    public SmtpEmailMessageService(ObjectProvider<JavaMailSender> mailSenders,
                                   @Value("${spring.mail.host:}") String host,
                                   @Value("${app.notification.email.from:}") String fromAddress) {
        this.mailSenders = mailSenders;
        this.host = host;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendEmail(List<String> recipients, String subject, String body,
                          List<MessageAttachment> attachments) {
        if (!StringUtils.hasText(host)) {
            throw new MessageDeliveryException("SMTP host is not configured");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new MessageDeliveryException("Email sender address is not configured");
        }
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            throw new MessageDeliveryException("SMTP mail sender is unavailable; check the SMTP configuration");
        }
        SmtpEmailDelivery.send(mailSender, fromAddress, "SMTP", recipients, subject, body, attachments);
    }
}
