package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import com.fse.fsenotification.util.MessageLogSanitizer;
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

    static void send(JavaMailSender mailSender, String fromAddress, String provider, Long messageId,
                     List<String> recipients, String subject, String body, List<MessageAttachment> attachments) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(),
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
//            helper.setBcc(recipients.toArray(String[]::new));
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, true);
            for (MessageAttachment attachment : attachments) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            log.info("{} email request: message={}, from={}, recipients={}, subject={}, body={}, attachmentCount={}",
                    provider, messageId, MessageLogSanitizer.maskRecipient(fromAddress),
                    MessageLogSanitizer.maskRecipients(recipients), MessageLogSanitizer.escape(subject),
                    MessageLogSanitizer.escape(body), attachments.size());
            mailSender.send(message);
            log.info("{} email response: message={}, status=accepted, recipientCount={}, attachmentCount={}",
                    provider, messageId, recipients.size(), attachments.size());
        } catch (MessagingException | MailException | IllegalArgumentException exception) {
            log.warn("{} email response: message={}, status=failed, exception={}, reason={}", provider, messageId,
                    exception.getClass().getSimpleName(),
                    MessageLogSanitizer.sanitizeResponse(exception.getMessage(), recipients));
            throw new MessageDeliveryException(provider + " email delivery failed", exception);
        }
    }
}
