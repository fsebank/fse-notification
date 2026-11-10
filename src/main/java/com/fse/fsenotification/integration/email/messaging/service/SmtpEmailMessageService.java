package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.fse.fsenotification.util.NotificationProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Properties;

@Service("SMTP")
@ConditionalOnProperty(name = "app.notification.email.provider", havingValue = "SMTP", matchIfMissing = true)
public class SmtpEmailMessageService implements EmailMessageService {

    private final JavaMailSenderImpl mailSender;
    private final NotificationProperties.Email email;

    public SmtpEmailMessageService(NotificationProperties properties) {
        this.email = properties.getEmail();
        NotificationProperties.Smtp smtp = email.getSmtp();
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(smtp.getHost());
        this.mailSender.setPort(smtp.getPort());
        this.mailSender.setUsername(smtp.getUsername());
        this.mailSender.setPassword(smtp.getPassword());
        Properties mailProperties = this.mailSender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", Boolean.toString(smtp.isAuth()));
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(smtp.isStarttls()));
    }

    @Override
    public void sendEmail(List<String> recipients, String subject, String body,
                          List<MessageAttachment> attachments) {
        if (!StringUtils.hasText(email.getSmtp().getHost())) {
            throw new MessageDeliveryException("SMTP host is not configured");
        }
        if (!StringUtils.hasText(email.getFrom())) {
            throw new MessageDeliveryException("Email sender address is not configured");
        }
        SmtpEmailDelivery.send(mailSender, email.getFrom(), "SMTP", recipients, subject, body, attachments);
    }
}
