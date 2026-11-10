package com.fse.fsenotification.integration.email.messaging.service;

import com.fse.fsenotification.modules.message.domain.MessageAttachment;

import java.util.List;

/** Sends a bulk email and any attachments through one configured provider. */
public interface EmailMessageService {

    void sendEmail(List<String> recipients, String subject, String body, List<MessageAttachment> attachments);
}
