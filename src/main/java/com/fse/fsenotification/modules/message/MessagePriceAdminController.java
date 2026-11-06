package com.fse.fsenotification.modules.message;

import com.fse.fsenotification.modules.message.MessagePriceDtos.CreateMessagePriceRequest;
import com.fse.fsenotification.modules.message.MessagePriceDtos.MessagePriceResponse;
import com.fse.fsenotification.modules.message.MessagePriceDtos.UpdateMessagePriceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only management of SMS and email prices. Secured by the admin filter chain (Bearer JWT, ROLE_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/message-prices")
@RequiredArgsConstructor
public class MessagePriceAdminController {

    private final MessagePriceService messagePriceService;

    @GetMapping
    public List<MessagePriceResponse> list() {
        return messagePriceService.findAll();
    }

    @GetMapping("/{id}")
    public MessagePriceResponse get(@PathVariable Long id) {
        return messagePriceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessagePriceResponse create(@Valid @RequestBody CreateMessagePriceRequest request,
                                       Authentication authentication) {
        return messagePriceService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public MessagePriceResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMessagePriceRequest request,
                                       Authentication authentication) {
        return messagePriceService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        messagePriceService.delete(id, authentication.getName());
    }
}
