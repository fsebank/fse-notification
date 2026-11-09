package com.fse.fsenotification.integration.sms.messaging.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SendSMSRequestDto {
    private String message;
    private String sender;
    private String recipient;
}
