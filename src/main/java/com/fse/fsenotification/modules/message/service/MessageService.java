package com.fse.fsenotification.modules.message.service;

import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.MessageResponse;
import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendEmailRequest;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendMessageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendSmsRequest;
import com.fse.fsenotification.security.PartnerPrincipal;

import java.util.List;

/**
 * Sends partner messages and charges them to the partner's access key balance.
 */
public interface MessageService {

    /**
     * Charges SMS pages x recipients and sends the SMS.
     *
     * @throws com.fse.fsenotification.common.InsufficientBalanceException if the balance does not cover the cost
     * @throws com.fse.fsenotification.common.ConflictException            if the reference was already used
     */
    SendMessageResponse sendSms(PartnerPrincipal partner, SendSmsRequest request);

    /**
     * Charges one unit per recipient and sends the email.
     *
     * @throws com.fse.fsenotification.common.InsufficientBalanceException if the balance does not cover the cost
     * @throws com.fse.fsenotification.common.ConflictException            if the reference was already used
     */
    SendMessageResponse sendEmail(PartnerPrincipal partner, SendEmailRequest request);

    /** Sends an email with already-uploaded attachment bytes. */
    SendMessageResponse sendEmail(PartnerPrincipal partner, SendEmailRequest request,
                                  List<MessageAttachment> attachments);

    /** @throws com.fse.fsenotification.common.ResourceNotFoundException if the message is not the partner's */
    MessageResponse findForPartner(PartnerPrincipal partner, Long messageId);

    PageResponse<MessageResponse> listForPartner(PartnerPrincipal partner, int page, int size);
}
