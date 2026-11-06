package com.fse.fsenotification.integration.sms.messaging.service;


import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;

public interface MessageService {

    SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto);
}
