package com.fse.fsenotification.integration.sms.messaging.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SendSMSResponseDto {

    private String message;
    private String messageId;
    private String smsCode;
    private String status;
}
