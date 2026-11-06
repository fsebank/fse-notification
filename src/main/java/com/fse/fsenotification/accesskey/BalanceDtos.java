package com.fse.fsenotification.accesskey;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class BalanceDtos {

    private BalanceDtos() {
    }

    public record CreditBalanceRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String reason) {
    }

    public record BalanceResponse(Long accessKeyId, String clientId, BigDecimal balance, String currency) {
    }

    public record BalanceTransactionResponse(Long id, BalanceTransactionType type, BigDecimal amount,
                                             BigDecimal balanceAfter, String currency, String description,
                                             Long messageId, String createdBy, Instant createdAt) {

        static BalanceTransactionResponse from(BalanceTransaction tx, String currency) {
            return new BalanceTransactionResponse(tx.getId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(),
                    currency, tx.getDescription(), tx.getMessageId(), tx.getCreatedBy(), tx.getCreatedAt());
        }
    }
}
