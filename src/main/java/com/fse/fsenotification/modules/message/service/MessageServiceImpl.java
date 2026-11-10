package com.fse.fsenotification.modules.message.service;

import com.fse.fsenotification.accesskey.BalanceService;
import com.fse.fsenotification.common.BadRequestException;
import com.fse.fsenotification.common.ConflictException;
import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.common.ResourceNotFoundException;
import com.fse.fsenotification.modules.message.*;
import com.fse.fsenotification.modules.message.domain.Message;
import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.dto.MessageDtos;
import com.fse.fsenotification.modules.message.dto.MessageDtos.EmailAttachmentRequest;
import com.fse.fsenotification.modules.message.dto.MessageDtos.MessageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendEmailRequest;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendMessageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendSmsRequest;
import com.fse.fsenotification.modules.message.enums.MessageChannel;
import com.fse.fsenotification.modules.message.enums.MessageStatus;
import com.fse.fsenotification.modules.message.repository.MessageRepository;
import com.fse.fsenotification.modules.message.sender.LoggingMessageSender;
import com.fse.fsenotification.modules.message.sender.MessageSender;
import com.fse.fsenotification.security.PartnerPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link MessageService}: charges the partner's access key balance and delivers through the channel's
 * {@link MessageSender}.
 * <p>
 * Flow: (1) in one transaction, save the message as PENDING and debit the balance (rolled back together if the
 * balance is insufficient); (2) hand it to the channel's sender outside any transaction; (3) mark it SENT, or
 * FAILED and refund the charge.
 * <p>
 * Bean is named explicitly because integration.sms.messaging.service.MessageServiceImpl would otherwise also
 * default to "messageServiceImpl".
 */
@Slf4j
@Service("partnerMessageService")
public class MessageServiceImpl implements MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final BalanceService balanceService;
    private final MessagePriceService pricing;
    private final TransactionTemplate transactionTemplate;
    private final Map<MessageChannel, MessageSender> senders = new EnumMap<>(MessageChannel.class);

    public MessageServiceImpl(MessageRepository messageRepository, BalanceService balanceService,
                              MessagePriceService pricing, PlatformTransactionManager transactionManager,
                              List<MessageSender> configuredSenders) {
        this.messageRepository = messageRepository;
        this.balanceService = balanceService;
        this.pricing = pricing;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        for (MessageSender sender : configuredSenders) {
            for (MessageChannel channel : MessageChannel.values()) {
                if (sender.supports(channel) && senders.putIfAbsent(channel, sender) != null) {
                    throw new IllegalStateException("Multiple message senders support " + channel);
                }
            }
        }
        for (MessageChannel channel : MessageChannel.values()) {
            if (!senders.containsKey(channel)) {
                log.warn("No message sender registered for {}; delivery will fail", channel);
                senders.put(channel, new LoggingMessageSender(channel));
            }
        }
    }

    @Override
    public SendMessageResponse sendSms(PartnerPrincipal partner, SendSmsRequest request) {
        Message message = new Message();
        message.setChannel(MessageChannel.SMS);
        message.setRecipientList(normalizePhones(request.recipients()));
        message.setSenderId(StringUtils.hasText(request.senderId()) ? request.senderId().trim() : null);
        message.setBody(request.message());
        message.setUnits(SmsSegments.count(request.message()));
        return dispatch(partner, message, request.reference());
    }

    @Override
    public SendMessageResponse sendEmail(PartnerPrincipal partner, SendEmailRequest request) {
        Set<String> recipients = new LinkedHashSet<>();
        request.recipients().forEach(r -> recipients.add(r.trim().toLowerCase(Locale.ROOT)));

        Message message = new Message();
        message.setChannel(MessageChannel.EMAIL);
        message.setRecipientList(List.copyOf(recipients));
        message.setSubject(request.subject().trim());
        message.setBody(request.body());
        message.setAttachments(decodeAttachments(request.attachments()));
        message.setUnits(1);
        return dispatch(partner, message, request.reference());
    }

    @Override
    public MessageResponse findForPartner(PartnerPrincipal partner, Long messageId) {
        return messageRepository.findByIdAndAccessKeyId(messageId, partner.accessKeyId())
                .map(m -> MessageResponse.from(m, balanceService.currency()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
    }

    @Override
    public PageResponse<MessageResponse> listForPartner(PartnerPrincipal partner, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                messageRepository.findByAccessKeyIdOrderByCreatedAtDescIdDesc(partner.accessKeyId(), pageRequest),
                m -> MessageResponse.from(m, balanceService.currency()));
    }

    // ---------------------------------------------------------------- internals

    private SendMessageResponse dispatch(PartnerPrincipal partner, Message message, String reference) {
        String clientReference = StringUtils.hasText(reference) ? reference.trim() : null;
        message.setAccessKeyId(partner.accessKeyId());
        message.setClientReference(clientReference);
        BigDecimal cost = pricing.cost(message.getChannel(), message.getUnits(), message.getRecipientCount());
        message.setCost(cost);

        // 1. Persist + charge atomically. InsufficientBalanceException rolls both back.
        Message saved = transactionTemplate.execute(status -> {
            if (clientReference != null
                    && messageRepository.existsByAccessKeyIdAndClientReference(partner.accessKeyId(), clientReference)) {
                throw new ConflictException("A message with reference '" + clientReference + "' already exists");
            }
            Message persisted = messageRepository.save(message);
            if (cost.signum() > 0) {
                balanceService.debit(partner.accessKeyId(), cost, describe(persisted), persisted.getId(),
                        partner.clientId());
            }
            return persisted;
        });

        // 2. Deliver outside the transaction so a slow provider does not hold a DB row lock.
        String failure = null;
        try {
            senders.get(saved.getChannel()).send(saved);
        } catch (RuntimeException ex) {
            failure = truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            log.warn("{} message {} for {} failed: {}", saved.getChannel(), saved.getId(), partner.clientId(),
                    failure, ex);
        }

        // 3. Record the outcome; refund on failure.
        String failureReason = failure;
        Message finished = transactionTemplate.execute(status -> {
            Message current = messageRepository.findById(saved.getId()).orElseThrow();
            if (failureReason == null) {
                current.setStatus(MessageStatus.SENT);
            } else {
                current.setStatus(MessageStatus.FAILED);
                current.setFailureReason(failureReason);
                if (cost.signum() > 0) {
                    balanceService.refund(partner.accessKeyId(), cost, "Refund: " + describe(current),
                            current.getId(), partner.clientId());
                }
            }
            return current;
        });

        log.info("{} message {} for {}: {} recipient(s), {} unit(s), cost {} {}, status {}", finished.getChannel(),
                finished.getId(), partner.clientId(), finished.getRecipientCount(), finished.getUnits(), cost,
                balanceService.currency(), finished.getStatus());
        String currency = balanceService.currency();
        return new SendMessageResponse(MessageResponse.from(finished, currency),
                balanceService.getBalance(partner.accessKeyId()).balance(), currency);
    }

    /** Strips formatting, converts local Nigerian numbers (0XXXXXXXXXX) to +234 and removes duplicates. */
    private static List<String> normalizePhones(List<String> phones) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : phones) {
            String digits = raw.replaceAll("[\\s()-]", "");
            boolean international = digits.startsWith("+");
            String number = international ? digits.substring(1) : digits;
            if (!number.matches("[0-9]{7,15}")) {
                throw new BadRequestException("Invalid phone number: " + raw);
            }
            if (!international && number.length() == 11 && number.startsWith("0")) {
                number = "234" + number.substring(1);
            }
            result.add("+" + number);
        }
        return List.copyOf(result);
    }

    private static String describe(Message message) {
        return "%s to %d recipient(s), %d unit(s), message #%d".formatted(message.getChannel(),
                message.getRecipientCount(), message.getUnits(), message.getId());
    }

    private static List<MessageAttachment> decodeAttachments(List<EmailAttachmentRequest> attachments) {
        List<MessageAttachment> decoded = new java.util.ArrayList<>();
        int totalBytes = 0;
        for (EmailAttachmentRequest attachment : attachments) {
            if (attachment == null) {
                throw new BadRequestException("Email attachment entries must not be null");
            }
            String fileName = attachment.fileName().trim();
            if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")
                    || fileName.contains("\r") || fileName.contains("\n")) {
                throw new BadRequestException("Attachment file names must not contain paths or line breaks");
            }
            String contentType;
            try {
                contentType = MimeTypeUtils.parseMimeType(attachment.contentType().trim()).toString();
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Attachment content type must be a valid MIME type");
            }

            byte[] content;
            try {
                content = Base64.getDecoder().decode(attachment.contentBase64());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Attachment contentBase64 must be valid Base64");
            }
            if (content.length == 0 || content.length > MessageDtos.MAX_EMAIL_ATTACHMENT_BYTES) {
                throw new BadRequestException("Each email attachment must be between 1 byte and 5 MB");
            }
            totalBytes += content.length;
            if (totalBytes > MessageDtos.MAX_EMAIL_ATTACHMENTS_TOTAL_BYTES) {
                throw new BadRequestException("Combined email attachments must not exceed 10 MB");
            }
            decoded.add(new MessageAttachment(fileName, contentType, content));
        }
        return List.copyOf(decoded);
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
