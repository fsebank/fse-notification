package com.fse.fsenotification.modules.message;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MessageDtos {

    /** Max recipients per request, to bound cost and provider payload size. */
    public static final int MAX_RECIPIENTS = 100;

    private MessageDtos() {
    }

    /**
     * @param recipients phone numbers in international format (e.g. +2348012345678) or local Nigerian format
     *                   (e.g. 08012345678, converted to +234...)
     * @param senderId   optional alphanumeric sender name, max 11 characters
     * @param reference  optional partner reference; reusing one returns 409 so a retry is never charged twice
     */
    public record SendSmsRequest(
            @NotEmpty @Size(max = MAX_RECIPIENTS)
            List<@NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number")
                    String> recipients,
            @NotBlank @Size(max = 1530) String message,
            @Size(max = 11) @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "must be letters, digits or spaces")
            String senderId,
            @Size(max = 100) String reference) {
    }

    /** @param reference optional partner reference; reusing one returns 409 so a retry is never charged twice */
    public record SendEmailRequest(
            @NotEmpty @Size(max = MAX_RECIPIENTS) List<@NotBlank @Email @Size(max = 254) String> recipients,
            @NotBlank @Size(max = 255) String subject,
            @NotBlank @Size(max = 100_000) String body,
            @Size(max = 100) String reference) {
    }

    public record MessageResponse(Long messageId, MessageChannel channel, MessageStatus status,
                                  List<String> recipients, int recipientCount, int units, BigDecimal cost,
                                  String currency, String reference, String failureReason, Instant createdAt) {

        static MessageResponse from(Message message, String currency) {
            return new MessageResponse(message.getId(), message.getChannel(), message.getStatus(),
                    message.getRecipientList(), message.getRecipientCount(), message.getUnits(), message.getCost(),
                    currency, message.getClientReference(), message.getFailureReason(), message.getCreatedAt());
        }
    }

    /** Send result: the message plus the partner's balance after the charge (and any refund). */
    public record SendMessageResponse(MessageResponse message, BigDecimal balance, String currency) {
    }
}
