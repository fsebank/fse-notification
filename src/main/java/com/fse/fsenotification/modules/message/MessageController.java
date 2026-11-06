package com.fse.fsenotification.modules.message;

import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.modules.message.MessageDtos.MessageResponse;
import com.fse.fsenotification.modules.message.MessageDtos.SendEmailRequest;
import com.fse.fsenotification.modules.message.MessageDtos.SendMessageResponse;
import com.fse.fsenotification.modules.message.MessageDtos.SendSmsRequest;
import com.fse.fsenotification.security.PartnerPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendEmail(@AuthenticationPrincipal PartnerPrincipal partner,
                                         @Valid @RequestBody SendEmailRequest request) {
        return messageService.sendEmail(partner, request);
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
}
