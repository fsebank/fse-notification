package com.fse.fsenotification.modules.message;

import com.fse.fsenotification.accesskey.BalanceService;
import com.fse.fsenotification.common.ConflictException;
import com.fse.fsenotification.common.ResourceNotFoundException;
import com.fse.fsenotification.common.ServiceUnavailableException;
import com.fse.fsenotification.modules.message.MessagePriceDtos.CreateMessagePriceRequest;
import com.fse.fsenotification.modules.message.MessagePriceDtos.MessagePriceResponse;
import com.fse.fsenotification.modules.message.MessagePriceDtos.UpdateMessagePriceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Admin-managed message prices, stored in message_price. A price change applies to messages sent afterwards;
 * each message keeps the cost it was charged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePriceService {

    private final MessagePriceRepository repository;

    /**
     * Cost of sending units x recipients on the channel.
     *
     * @throws ServiceUnavailableException if no price is configured for the channel
     */
    @Transactional(readOnly = true)
    public BigDecimal cost(MessageChannel channel, int units, int recipients) {
        BigDecimal unitPrice = repository.findByChannel(channel)
                .map(MessagePrice::getUnitPrice)
                .orElseThrow(() -> new ServiceUnavailableException(
                        channel + " sending is not available: no price is configured"));
        return unitPrice.multiply(BigDecimal.valueOf((long) units * recipients)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<MessagePriceResponse> findAll() {
        return repository.findAllByOrderByChannelAsc().stream().map(MessagePriceService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MessagePriceResponse findById(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public MessagePriceResponse create(CreateMessagePriceRequest request, String actor) {
        if (repository.existsByChannel(request.channel())) {
            throw new ConflictException("A price for " + request.channel() + " already exists; update it instead");
        }
        MessagePrice price = new MessagePrice();
        price.setChannel(request.channel());
        price.setUnitPrice(scale(request.unitPrice()));
        price.setDescription(trimToNull(request.description()));
        price.setCreatedBy(actor);
        price.setUpdatedBy(actor);
        MessagePrice saved = repository.save(price);
        log.info("{} price created at {} {} by {}", saved.getChannel(), saved.getUnitPrice(),
                BalanceService.CURRENCY, actor);
        return toResponse(saved);
    }

    @Transactional
    public MessagePriceResponse update(Long id, UpdateMessagePriceRequest request, String actor) {
        MessagePrice price = load(id);
        BigDecimal previous = price.getUnitPrice();
        price.setUnitPrice(scale(request.unitPrice()));
        price.setDescription(trimToNull(request.description()));
        price.setUpdatedBy(actor);
        MessagePrice saved = repository.saveAndFlush(price);
        log.info("{} price changed from {} to {} {} by {}", saved.getChannel(), previous, saved.getUnitPrice(),
                BalanceService.CURRENCY, actor);
        return toResponse(saved);
    }

    /** After deletion, sending on the channel is rejected with 503 until a new price is created. */
    @Transactional
    public void delete(Long id, String actor) {
        MessagePrice price = load(id);
        repository.delete(price);
        log.warn("{} price deleted by {}; {} sending is disabled until a new price is created", price.getChannel(),
                actor, price.getChannel());
    }

    private MessagePrice load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Message price not found"));
    }

    private static MessagePriceResponse toResponse(MessagePrice price) {
        return MessagePriceResponse.from(price, BalanceService.CURRENCY);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
