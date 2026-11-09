package com.fse.fsenotification.integration.email.messaging.service;

import java.util.List;

/** Sends a bulk email through one configured provider. */
public interface EmailMessageService {

    void sendEmail(List<String> recipients, String subject, String body);
}
