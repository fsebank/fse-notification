package com.fse.fsenotification.integration.sms.messaging.service;

import com.fse.fsenotification.integration.sms.messaging.dto.request.SendSMSRequestDto;
import com.fse.fsenotification.integration.sms.messaging.dto.response.SendSMSResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class MessageServiceImpl implements MessageService {



    @Override
    public SendSMSResponseDto sendSMS(SendSMSRequestDto sendSMSRequestDto) {
        return null;
    }
}
