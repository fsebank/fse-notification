package com.fse.fsenotification.modules.message.domain;

/** In-memory attachment data passed to an email provider; attachment bytes are not persisted with the message. */
public record MessageAttachment(String fileName, String contentType, byte[] content) {
}
