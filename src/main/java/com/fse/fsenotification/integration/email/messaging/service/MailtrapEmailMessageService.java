package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import com.fse.fsenotification.util.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Properties;

/** Mailtrap SMTP provider. For production delivery, use credentials for a verified sending domain. */
@Service("MAILTRAP")
@ConditionalOnProperty(name = "app.notification.email.provider", havingValue = "MAILTRAP")
public class MailtrapEmailMessageService implements EmailMessageService {

    private final JavaMailSenderImpl mailSender;
    private final NotificationProperties.Mailtrap mailtrap;

    public MailtrapEmailMessageService(NotificationProperties properties) {
        this.mailtrap = properties.getEmail().getMailtrap();
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(mailtrap.getHost());
        this.mailSender.setPort(mailtrap.getPort());
        this.mailSender.setUsername(mailtrap.getUsername());
        this.mailSender.setPassword(mailtrap.getPassword());
        Properties mailProperties = this.mailSender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(mailtrap.isStarttls()));
    }

    @Override
    public void sendEmail(List<String> recipients, String subject, String body,
                          List<MessageAttachment> attachments) {
        if (!StringUtils.hasText(mailSender.getHost())
                || !StringUtils.hasText(mailSender.getUsername())
                || !StringUtils.hasText(mailSender.getPassword())
                || !StringUtils.hasText(mailtrap.getFrom())) {
            throw new MessageDeliveryException(
                    "Mailtrap host, username, password and sender address must be configured");
        }
        SmtpEmailDelivery.send(mailSender, mailtrap.getFrom(), "Mailtrap", recipients, subject, body, attachments);
    }
}
