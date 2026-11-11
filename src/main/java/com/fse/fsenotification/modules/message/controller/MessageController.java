package com.fse.fsenotification.modules.message.controller;

import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.common.BadRequestException;
import com.fse.fsenotification.modules.message.domain.MessageAttachment;
import com.fse.fsenotification.modules.message.dto.MessageDtos;
import com.fse.fsenotification.modules.message.dto.MessageDtos.MessageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendEmailForm;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendEmailRequest;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendMessageResponse;
import com.fse.fsenotification.modules.message.dto.MessageDtos.SendSmsRequest;
import com.fse.fsenotification.modules.message.service.MessageService;
import com.fse.fsenotification.security.PartnerPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Partner messaging API. Authenticated by the partner filter chain with X-Client-Id / X-Client-Secret headers
 * (no Bearer token); each send is charged to the calling access key's balance.
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/sms")
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendSms(@AuthenticationPrincipal PartnerPrincipal partner,
                                       @Valid @RequestBody SendSmsRequest request) {
        return messageService.sendSms(partner, request);
    }

    @PostMapping(value = "/email", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendEmail(@AuthenticationPrincipal PartnerPrincipal partner,
                                         @Valid @RequestBody SendEmailRequest request) {
        return messageService.sendEmail(partner, request);
    }

    @PostMapping(value = "/email", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendEmailWithAttachments(@AuthenticationPrincipal PartnerPrincipal partner,
                                                        @Valid @ModelAttribute SendEmailForm form,
                                                        @RequestParam(value = "attachments", required = false)
                                                        List<MultipartFile> attachments) {
        SendEmailRequest request = new SendEmailRequest(form.getRecipients(), form.getSubject(), form.getBody(),
                form.getReference(), List.of());
        return messageService.sendEmail(partner, request, readAttachments(attachments));
    }

    @GetMapping("/{id}")
    public MessageResponse get(@AuthenticationPrincipal PartnerPrincipal partner, @PathVariable Long id) {
        return messageService.findForPartner(partner, id);
    }

    @GetMapping
    public PageResponse<MessageResponse> list(@AuthenticationPrincipal PartnerPrincipal partner,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return messageService.listForPartner(partner, page, size);
    }

    private static List<MessageAttachment> readAttachments(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > MessageDtos.MAX_EMAIL_ATTACHMENTS) {
            throw new BadRequestException("An email can have at most 5 attachments");
        }

        List<MessageAttachment> attachments = new ArrayList<>(files.size());
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BadRequestException("Uploaded attachments must not be empty");
            }
            if (file.getSize() > MessageDtos.MAX_EMAIL_ATTACHMENT_BYTES) {
                throw new BadRequestException("Each email attachment must not exceed 5 MB");
            }
            totalBytes += file.getSize();
            if (totalBytes > MessageDtos.MAX_EMAIL_ATTACHMENTS_TOTAL_BYTES) {
                throw new BadRequestException("Combined email attachments must not exceed 10 MB");
            }

            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            try {
                attachments.add(new MessageAttachment(fileName, contentType, file.getBytes()));
            } catch (IOException ex) {
                throw new BadRequestException("Could not read uploaded attachment");
            }
        }
        return List.copyOf(attachments);
    }
}
