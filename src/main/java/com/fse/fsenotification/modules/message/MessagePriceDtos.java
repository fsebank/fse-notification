package com.fse.fsenotification.modules.message;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class MessagePriceDtos {

    private MessagePriceDtos() {
    }

    public record CreateMessagePriceRequest(
            @NotNull MessageChannel channel,
            @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal unitPrice,
            @Size(max = 255) String description) {
    }

    /** The channel cannot be changed; delete the price and create one for the other channel instead. */
    public record UpdateMessagePriceRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal unitPrice,
            @Size(max = 255) String description) {
    }

    public record MessagePriceResponse(Long id, MessageChannel channel, BigDecimal unitPrice, String currency,
                                       String description, String createdBy, String updatedBy, Instant createdAt,
                                       Instant updatedAt) {

        static MessagePriceResponse from(MessagePrice price, String currency) {
            return new MessagePriceResponse(price.getId(), price.getChannel(), price.getUnitPrice(), currency,
                    price.getDescription(), price.getCreatedBy(), price.getUpdatedBy(), price.getCreatedAt(),
                    price.getUpdatedAt());
        }
    }
}
