package com.fse.fsenotification.integration.sms.messaging.dto.request;

import lombok.Data;

@Data
public class SendSMSRequestDto {
    private String message;
    private String sender;
    private String recipient;
}
