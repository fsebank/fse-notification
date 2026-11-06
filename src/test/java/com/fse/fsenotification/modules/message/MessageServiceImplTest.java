package com.fse.fsenotification.modules.message;

import com.fse.fsenotification.accesskey.BalanceDtos.BalanceResponse;
import com.fse.fsenotification.accesskey.BalanceService;
import com.fse.fsenotification.common.ConflictException;
import com.fse.fsenotification.common.InsufficientBalanceException;
import com.fse.fsenotification.common.ServiceUnavailableException;
import com.fse.fsenotification.modules.message.MessageDtos.SendMessageResponse;
import com.fse.fsenotification.modules.message.MessageDtos.SendSmsRequest;
import com.fse.fsenotification.modules.message.sender.MessageDeliveryException;
import com.fse.fsenotification.modules.message.sender.MessageSender;
import com.fse.fsenotification.security.PartnerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageServiceImplTest {

    private static final PartnerPrincipal PARTNER = new PartnerPrincipal(1L, "fse_abc", "Acme Ltd");

    private final MessageRepository repository = mock(MessageRepository.class);
    private final BalanceService balanceService = mock(BalanceService.class);
    private final MessagePriceRepository priceRepository = mock(MessagePriceRepository.class);
    private final MessagePriceService pricing = new MessagePriceService(priceRepository);
    private final MessageSender smsSender = mock(MessageSender.class);
    private final AtomicReference<Message> stored = new AtomicReference<>();
    private MessageService service;

    @BeforeEach
    void setUp() {
        given(smsSender.channel()).willReturn(MessageChannel.SMS);
        given(priceRepository.findByChannel(MessageChannel.SMS))
                .willReturn(Optional.of(price(MessageChannel.SMS, "4.00")));
        given(balanceService.getBalance(1L)).willReturn(new BalanceResponse(1L, "fse_abc", new BigDecimal("92.00"), "NGN"));
        given(repository.save(any())).willAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(10L);
            stored.set(m);
            return m;
        });
        given(repository.findById(10L)).willAnswer(inv -> Optional.ofNullable(stored.get()));

        service = new MessageServiceImpl(repository, balanceService, pricing, mock(PlatformTransactionManager.class),
                List.of(smsSender));
    }

    @Test
    void chargesPerPagePerRecipientAndNormalizesNumbers() {
        SendSmsRequest request = new SendSmsRequest(List.of("08012345678", "+234 801 234 5678", "+447700900123"),
                "a".repeat(200), null, null);

        SendMessageResponse response = service.sendSms(PARTNER, request);

        // 2 distinct recipients (the first two are the same number) x 2 pages x 4.00
        assertThat(response.message().recipients()).containsExactly("+2348012345678", "+447700900123");
        assertThat(response.message().units()).isEqualTo(2);
        assertThat(response.message().cost()).isEqualByComparingTo("16.00");
        assertThat(response.message().status()).isEqualTo(MessageStatus.SENT);
        assertThat(response.balance()).isEqualByComparingTo("92.00");
        verify(balanceService).debit(eq(1L), eq(new BigDecimal("16.00")), anyString(), eq(10L), eq("fse_abc"));
        verify(balanceService, never()).refund(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void failedDeliveryIsRefunded() {
        willThrow(new MessageDeliveryException("Provider unavailable")).given(smsSender).send(any());

        SendMessageResponse response = service.sendSms(PARTNER,
                new SendSmsRequest(List.of("08012345678"), "Hello", null, null));

        assertThat(response.message().status()).isEqualTo(MessageStatus.FAILED);
        assertThat(response.message().failureReason()).isEqualTo("Provider unavailable");
        verify(balanceService).refund(eq(1L), eq(new BigDecimal("4.00")), anyString(), eq(10L), eq("fse_abc"));
    }

    @Test
    void insufficientBalanceIsNotSent() {
        given(balanceService.debit(anyLong(), any(), anyString(), anyLong(), anyString()))
                .willThrow(new InsufficientBalanceException("Insufficient balance"));

        assertThatThrownBy(() -> service.sendSms(PARTNER,
                new SendSmsRequest(List.of("08012345678"), "Hello", null, null)))
                .isInstanceOf(InsufficientBalanceException.class);
        verify(smsSender, never()).send(any());
    }

    @Test
    void reusedReferenceIsRejectedWithoutCharging() {
        given(repository.existsByAccessKeyIdAndClientReference(1L, "INV-1")).willReturn(true);

        assertThatThrownBy(() -> service.sendSms(PARTNER,
                new SendSmsRequest(List.of("08012345678"), "Hello", null, "INV-1")))
                .isInstanceOf(ConflictException.class);
        verify(balanceService, never()).debit(anyLong(), any(), anyString(), anyLong(), anyString());
        verify(smsSender, never()).send(any());
    }

    @Test
    void channelWithoutPriceIsRejectedWithoutCharging() {
        given(priceRepository.findByChannel(MessageChannel.SMS)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendSms(PARTNER,
                new SendSmsRequest(List.of("08012345678"), "Hello", null, null)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("no price is configured");
        verify(repository, never()).save(any());
        verify(smsSender, never()).send(any());
    }

    private static MessagePrice price(MessageChannel channel, String unitPrice) {
        MessagePrice price = new MessagePrice();
        price.setChannel(channel);
        price.setUnitPrice(new BigDecimal(unitPrice));
        return price;
    }
}
