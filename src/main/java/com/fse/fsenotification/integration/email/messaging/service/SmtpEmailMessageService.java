package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service("SMTP")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
public class SmtpEmailMessageService implements EmailMessageService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${app.notification.email.from:}")
    private String fromAddress;

    @Override
    public void sendEmail(List<String> recipients, String subject, String body) {
        if (!StringUtils.hasText(host)) {
            throw new MessageDeliveryException("SMTP host is not configured");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new MessageDeliveryException("Email sender address is not configured");
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setBcc(recipients.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("SMTP accepted email for {} recipient(s)", recipients.size());
        } catch (MessagingException | MailException exception) {
            throw new MessageDeliveryException("SMTP email delivery failed", exception);
        }
    }
}
