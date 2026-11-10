package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Properties;

/** Mailtrap SMTP provider. For production delivery, use credentials for a verified sending domain. */
@Service("MAILTRAP")
@ConditionalOnProperty(name = "email.provider", havingValue = "MAILTRAP")
public class MailtrapEmailMessageService implements EmailMessageService {

    private final JavaMailSenderImpl mailSender;
    private final String fromAddress;

    public MailtrapEmailMessageService(
            @Value("${app.notification.email.mailtrap.host:${MAILTRAP_HOST:sandbox.smtp.mailtrap.io}}") String host,
            @Value("${app.notification.email.mailtrap.port:${MAILTRAP_PORT:2525}}") int port,
            @Value("${app.notification.email.mailtrap.username:${MAILTRAP_USERNAME:}}") String username,
            @Value("${app.notification.email.mailtrap.password:${MAILTRAP_PASSWORD:}}") String password,
            @Value("${app.notification.email.mailtrap.from:${MAILTRAP_FROM:}}") String fromAddress,
            @Value("${app.notification.email.mailtrap.starttls:${MAILTRAP_STARTTLS_ENABLE:true}}") boolean startTls) {
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(host);
        this.mailSender.setPort(port);
        this.mailSender.setUsername(username);
        this.mailSender.setPassword(password);
        Properties mailProperties = this.mailSender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendEmail(List<String> recipients, String subject, String body,
                          List<MessageAttachment> attachments) {
        if (!StringUtils.hasText(mailSender.getHost())
                || !StringUtils.hasText(mailSender.getUsername())
                || !StringUtils.hasText(mailSender.getPassword())
                || !StringUtils.hasText(fromAddress)) {
            throw new MessageDeliveryException(
                    "Mailtrap host, username, password and sender address must be configured");
        }
        SmtpEmailDelivery.send(mailSender, fromAddress, "Mailtrap", recipients, subject, body, attachments);
    }
}
