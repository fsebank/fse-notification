package com.fse.fsenotification.integration.sms.messaging.service;


import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;

/**
 * Sends SMS through one provider. Return a {@code Success} status only when all recipients were accepted;
 * throw a delivery exception when the provider request fails.
 */
public interface MessageService {

    SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto);
}
