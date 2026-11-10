package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
final class SmtpEmailDelivery {

    private SmtpEmailDelivery() {
    }

    static void send(JavaMailSender mailSender, String fromAddress, String provider,
                     List<String> recipients, String subject, String body, List<MessageAttachment> attachments) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(),
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setBcc(recipients.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, false);
            for (MessageAttachment attachment : attachments) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(message);
            log.info("{} accepted email for {} recipient(s) with {} attachment(s)", provider,
                    recipients.size(), attachments.size());
        } catch (MessagingException | MailException | IllegalArgumentException exception) {
            throw new MessageDeliveryException(provider + " email delivery failed", exception);
        }
    }
}
