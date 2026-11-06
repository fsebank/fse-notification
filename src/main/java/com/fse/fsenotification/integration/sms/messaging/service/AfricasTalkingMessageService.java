package com.fse.fsenotification.integration.sms.messaging.service;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;
import com.fse.fsenotification.util.AfricaTalkingProperties;
import com.nimbusds.jose.shaded.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Primary
@Service("AFRICASTALKINGSMS")
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingMessageService implements MessageService {

    @Value("${sms.sendername}")
    private String senderName;

    private final AfricaTalkingProperties properties;
    private final ObjectMapper objectMapper;
   // private static Gson gson = new Gson();
    @Override
    public SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto) {
        AfricasTalking.initialize(properties.getUsername(), properties.getApiKey());

        log.info("Sending sms to AFRICASTALKINGSMS with username {} ", properties.getUsername());
        log.info("The recipient is {} and senderId {}" , sendSMSRequestDto.getRecipient(), sendSMSRequestDto.getSender());
        // Get SMS service
        SmsService sms = AfricasTalking.getService(SmsService.class);

       String senderId = sendSMSRequestDto.getSender();
       if(senderId == null) {
            senderId = senderName;
       }
       try {
           String[] formattedRecipients = formatRecipients(sendSMSRequestDto.getRecipient());
           List<Recipient> response = sms.send(sendSMSRequestDto.getMessage(), senderId, formattedRecipients, false);

           log.info("Sending {} recipients to sender {}", response.size(), senderId);
           ///log.info(response.toString());
           System.out.println(response);
           log.info("Information");
           if (response != null && !response.isEmpty()) {
               Recipient first = response.get(0);
               log.info("Sending message id {} status is {} and cost is {}", first.messageId, first.status, first.cost);
               return SendSMSResponseDto.builder()
                       .messageId(first.messageId)
                       .status(first.status)
                       .build();
           }
       } catch (IOException exception) {
           log.error("The error message is for  {}", exception.getMessage(), exception);
       }

       return SendSMSResponseDto.builder().status("FAILED").build();
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
