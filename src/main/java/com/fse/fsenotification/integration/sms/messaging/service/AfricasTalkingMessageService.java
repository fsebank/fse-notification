package com.fse.fsenotification.integration.sms.messaging.service;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import com.fse.fsenotification.util.AfricaTalkingProperties;
import com.fse.fsenotification.util.MessageLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service("AFRICASTALKINGSMS")
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingMessageService implements MessageService {

    private final AfricaTalkingProperties properties;

    @Override
    public SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto) {
        AfricasTalking.initialize(properties.getUsername(), properties.getApiKey());

        SmsService sms = AfricasTalking.getService(SmsService.class);

        String senderId = sendSMSRequestDto.getSender();
        if (senderId == null || senderId.isBlank()) {
            senderId = properties.getSenderName();
        }

        String[] formattedRecipients = formatRecipients(sendSMSRequestDto.getRecipient());
        log.info("Sending SMS through Africa's Talking: message={}, recipientCount={}, recipients={}",
                sendSMSRequestDto.getMessageId(), formattedRecipients.length,
                MessageLogSanitizer.maskRecipients(Arrays.asList(formattedRecipients)));
        List<Recipient> response;
        try {
            log.info("Africa's Talking SMS request: URL={}, body={}", properties.getBaseUrl(), sendSMSRequestDto);
            response = sms.send(sendSMSRequestDto.getMessage(), senderId, formattedRecipients, false);
        } catch (IOException exception) {
            log.warn("Africa's Talking SMS response: message={}, status=failed, exception={}, reason={}",
                    sendSMSRequestDto.getMessageId(), exception.getClass().getSimpleName(),
                    MessageLogSanitizer.sanitizeResponse(exception.getMessage(), Arrays.asList(formattedRecipients)));
            throw new MessageDeliveryException("Africa's Talking SMS request failed", exception);
        } catch (RuntimeException exception) {
            log.warn("Africa's Talking SMS response: message={}, status=failed, exception={}, reason={}",
                    sendSMSRequestDto.getMessageId(), exception.getClass().getSimpleName(),
                    MessageLogSanitizer.sanitizeResponse(exception.getMessage(), Arrays.asList(formattedRecipients)));
            throw new MessageDeliveryException("Africa's Talking SMS request failed", exception);
        }

        if (response != null) {
            for (int i = 0; i < response.size(); i++) {
                Recipient recipient = response.get(i);
                String maskedRecipient = i < formattedRecipients.length
                        ? MessageLogSanitizer.maskRecipient(formattedRecipients[i]) : "<unmatched>";
                String status = recipient == null || recipient.status == null ? "no response"
                        : MessageLogSanitizer.sanitizeResponse(recipient.status, Arrays.asList(formattedRecipients));
                String providerMessageId = recipient == null ? null
                        : MessageLogSanitizer.escape(recipient.messageId);
                if ("success".equalsIgnoreCase(status)) {
                    log.info("Africa's Talking SMS recipient response: message={}, recipient={}, status={}, "
                                    + "providerMessageId={}", sendSMSRequestDto.getMessageId(), maskedRecipient,
                            status, providerMessageId);
                } else {
                    log.warn("Africa's Talking SMS recipient response: message={}, recipient={}, status={}, "
                                    + "providerMessageId={}", sendSMSRequestDto.getMessageId(), maskedRecipient,
                            status, providerMessageId);
                }
            }
        }

        if (response == null || response.size() != formattedRecipients.length) {
            log.warn("Africa's Talking SMS response: message={}, status=incomplete, expectedRecipientCount={}, "
                            + "receivedRecipientCount={}", sendSMSRequestDto.getMessageId(),
                    formattedRecipients.length, response == null ? 0 : response.size());
            throw new MessageDeliveryException("Africa's Talking returned an incomplete SMS response");
        }

        List<Recipient> failedRecipients = response.stream()
                .filter(recipient -> recipient == null || recipient.status == null
                        || !"success".equalsIgnoreCase(recipient.status))
                .toList();
        if (!failedRecipients.isEmpty()) {
            log.warn("Africa's Talking SMS response: message={}, status=partially-failed, "
                            + "failedRecipientCount={}, recipientCount={}", sendSMSRequestDto.getMessageId(),
                    failedRecipients.size(), formattedRecipients.length);
            throw new MessageDeliveryException("Africa's Talking rejected %d of %d SMS recipient(s)"
                    .formatted(failedRecipients.size(), formattedRecipients.length));
        }

        Recipient first = response.get(0);
        log.info("Africa's Talking SMS response: message={}, status=accepted, recipientCount={}",
                sendSMSRequestDto.getMessageId(), response.size());
        return SendSMSResponseDto.builder()
                .messageId(first.messageId)
                .status(first.status)
                .build();
    }

    private String[] formatRecipients(String recipients) {
        return java.util.Arrays.stream(recipients.split(","))
                .map(String::trim)
                .map(number -> {
                    if (number.startsWith("0")) {
                        return "+234" + number.substring(1);
                    } else if (!number.startsWith("+") && number.length() < 13) {
                        return "+234" + number;
                    } else if (!number.startsWith("+")) {
                        return "+" + number;
                    }
                    return number;
                })
                .toArray(String[]::new);
    }

}
