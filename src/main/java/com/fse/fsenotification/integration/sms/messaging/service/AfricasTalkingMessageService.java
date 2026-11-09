package com.fse.fsenotification.integration.sms.messaging.service;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import com.fse.fsenotification.util.AfricaTalkingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service("AFRICASTALKINGSMS")
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingMessageService implements MessageService {

    @Value("${sms.sendername}")
    private String senderName;

    private final AfricaTalkingProperties properties;

    @Override
    public SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto) {
        AfricasTalking.initialize(properties.getUsername(), properties.getApiKey());

        SmsService sms = AfricasTalking.getService(SmsService.class);

        String senderId = sendSMSRequestDto.getSender();
        if (senderId == null || senderId.isBlank()) {
            senderId = senderName;
        }

        String[] formattedRecipients = formatRecipients(sendSMSRequestDto.getRecipient());
        log.info("Sending SMS through Africa's Talking to {} recipient(s)", formattedRecipients.length);
        try {
            List<Recipient> response = sms.send(sendSMSRequestDto.getMessage(), senderId, formattedRecipients, false);
            if (response == null || response.size() != formattedRecipients.length) {
                throw new MessageDeliveryException("Africa's Talking returned an incomplete SMS response");
            }

            List<Recipient> failedRecipients = response.stream()
                    .filter(recipient -> recipient == null || recipient.status == null
                            || !"success".equalsIgnoreCase(recipient.status))
                    .toList();
            if (!failedRecipients.isEmpty()) {
                throw new MessageDeliveryException("Africa's Talking rejected %d of %d SMS recipient(s)"
                        .formatted(failedRecipients.size(), formattedRecipients.length));
            }

            Recipient first = response.get(0);
            log.info("Africa's Talking accepted SMS for {} recipient(s)", response.size());
            return SendSMSResponseDto.builder()
                    .messageId(first.messageId)
                    .status(first.status)
                    .build();
        } catch (IOException exception) {
            throw new MessageDeliveryException("Africa's Talking SMS request failed", exception);
        }
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
