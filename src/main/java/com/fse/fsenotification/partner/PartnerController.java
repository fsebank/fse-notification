package com.fse.fsenotification.partner;

import com.fse.fsenotification.accesskey.BalanceDtos.BalanceResponse;
import com.fse.fsenotification.accesskey.BalanceDtos.BalanceTransactionResponse;
import com.fse.fsenotification.accesskey.BalanceService;
import com.fse.fsenotification.common.PageResponse;
import com.fse.fsenotification.security.PartnerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partner self-service. Any controller under /api/** (outside /api/v1/admin/**) is protected by the
 * partner access key filter in the same way.
 */
@RestController
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
public class PartnerController {

    private final BalanceService balanceService;

    /** Lets a partner verify that their access key works. */
    @GetMapping("/me")
    public PartnerPrincipal me(@AuthenticationPrincipal PartnerPrincipal partner) {
        return partner;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@AuthenticationPrincipal PartnerPrincipal partner) {
        return balanceService.getBalance(partner.accessKeyId());
    }

    @GetMapping("/balance/transactions")
    public PageResponse<BalanceTransactionResponse> transactions(@AuthenticationPrincipal PartnerPrincipal partner,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return balanceService.history(partner.accessKeyId(), page, size);
    }
}
