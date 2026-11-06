package com.fse.fsenotification.accesskey;

import com.fse.fsenotification.accesskey.BalanceDtos.BalanceResponse;
import com.fse.fsenotification.accesskey.BalanceDtos.BalanceTransactionResponse;
import com.fse.fsenotification.common.BadRequestException;
import com.fse.fsenotification.common.InsufficientBalanceException;
import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.common.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All balance changes go through here. Each change is a single atomic SQL update on the access key row plus an
 * immutable ledger entry, in the same transaction, so concurrent sends can never overspend or lose an update.
 */
@Slf4j
@Service
public class BalanceService {

    /** Currency of every balance and price. Balances are stored as plain amounts, so this must never change. */
    public static final String CURRENCY = "NGN";

    private static final int MAX_PAGE_SIZE = 100;

    private final PartnerAccessKeyRepository accessKeyRepository;
    private final BalanceTransactionRepository transactionRepository;

    public BalanceService(PartnerAccessKeyRepository accessKeyRepository,
                          BalanceTransactionRepository transactionRepository) {
        this.accessKeyRepository = accessKeyRepository;
        this.transactionRepository = transactionRepository;
    }

    public String currency() {
        return CURRENCY;
    }

    /** Admin top-up. */
    @Transactional
    public BalanceTransactionResponse credit(Long accessKeyId, BigDecimal amount, String reason, String actor) {
        PartnerAccessKey key = accessKeyRepository.findById(accessKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Access key not found"));
        if (key.getStatus() == AccessKeyStatus.REVOKED) {
            throw new BadRequestException("Access key is revoked and can no longer be credited");
        }
        BalanceTransaction tx = apply(accessKeyId, BalanceTransactionType.CREDIT, scale(amount), reason.trim(),
                null, actor);
        log.info("Access key {} credited {} {} by {} (balance {})", key.getClientId(), tx.getAmount(), CURRENCY,
                actor, tx.getBalanceAfter());
        return BalanceTransactionResponse.from(tx, CURRENCY);
    }

    /**
     * Charges a message. Must run inside the caller's transaction so the charge rolls back with the message.
     *
     * @throws InsufficientBalanceException if the balance is lower than amount (nothing is charged)
     */
    @Transactional
    public BalanceTransaction debit(Long accessKeyId, BigDecimal amount, String description, Long messageId,
                                    String actor) {
        BigDecimal scaled = scale(amount);
        if (accessKeyRepository.debitBalance(accessKeyId, scaled) == 0) {
            BigDecimal available = currentBalance(accessKeyId);
            throw new InsufficientBalanceException("Insufficient balance: required %s %s, available %s %s"
                    .formatted(scaled, CURRENCY, available, CURRENCY));
        }
        return record(accessKeyId, BalanceTransactionType.DEBIT, scaled, description, messageId, actor);
    }

    /** Returns a message charge after a failed send. */
    @Transactional
    public BalanceTransaction refund(Long accessKeyId, BigDecimal amount, String description, Long messageId,
                                     String actor) {
        return apply(accessKeyId, BalanceTransactionType.REFUND, scale(amount), description, messageId, actor);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accessKeyId) {
        PartnerAccessKey key = accessKeyRepository.findById(accessKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Access key not found"));
        return new BalanceResponse(key.getId(), key.getClientId(), currentBalance(accessKeyId), CURRENCY);
    }

    @Transactional(readOnly = true)
    public PageResponse<BalanceTransactionResponse> history(Long accessKeyId, int page, int size) {
        if (!accessKeyRepository.existsById(accessKeyId)) {
            throw new ResourceNotFoundException("Access key not found");
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                transactionRepository.findByAccessKeyIdOrderByCreatedAtDescIdDesc(accessKeyId, pageRequest),
                tx -> BalanceTransactionResponse.from(tx, CURRENCY));
    }

    private BalanceTransaction apply(Long accessKeyId, BalanceTransactionType type, BigDecimal amount,
                                     String description, Long messageId, String actor) {
        if (accessKeyRepository.creditBalance(accessKeyId, amount) == 0) {
            throw new ResourceNotFoundException("Access key not found");
        }
        return record(accessKeyId, type, amount, description, messageId, actor);
    }

    private BalanceTransaction record(Long accessKeyId, BalanceTransactionType type, BigDecimal amount,
                                      String description, Long messageId, String actor) {
        BalanceTransaction tx = new BalanceTransaction();
        tx.setAccessKeyId(accessKeyId);
        tx.setType(type);
        tx.setAmount(amount);
        // The row is locked by the update above until commit, so this read is the balance this change produced.
        tx.setBalanceAfter(currentBalance(accessKeyId));
        tx.setDescription(description);
        tx.setMessageId(messageId);
        tx.setCreatedBy(actor);
        return transactionRepository.save(tx);
    }

    private BigDecimal currentBalance(Long accessKeyId) {
        return accessKeyRepository.findBalanceById(accessKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Access key not found"))
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
