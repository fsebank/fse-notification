package com.fse.fsenotification.accesskey;

import com.fse.fsenotification.accesskey.AccessKeyDtos.AccessKeyCredentialsResponse;
import com.fse.fsenotification.accesskey.AccessKeyDtos.AccessKeyResponse;
import com.fse.fsenotification.accesskey.AccessKeyDtos.CreateAccessKeyRequest;
import com.fse.fsenotification.accesskey.AccessKeyDtos.UpdateAccessKeyRequest;
import com.fse.fsenotification.accesskey.AccessKeyDtos.UpdateAccessKeyStatusRequest;
import com.fse.fsenotification.accesskey.BalanceDtos.BalanceResponse;
import com.fse.fsenotification.accesskey.BalanceDtos.BalanceTransactionResponse;
import com.fse.fsenotification.accesskey.BalanceDtos.CreditBalanceRequest;
import com.fse.fsenotification.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only management of partner access keys. Secured by the admin filter chain (Bearer JWT, ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/access-keys")
@RequiredArgsConstructor
public class AccessKeyAdminController {

    private final AccessKeyService accessKeyService;
    private final BalanceService balanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessKeyCredentialsResponse create(@Valid @RequestBody CreateAccessKeyRequest request,
                                               Authentication authentication) {
        return accessKeyService.create(request, authentication.getName());
    }

    @GetMapping
    public List<AccessKeyResponse> list() {
        return accessKeyService.findAll();
    }

    @GetMapping("/{id}")
    public AccessKeyResponse get(@PathVariable Long id) {
        return accessKeyService.findById(id);
    }

    @PutMapping("/{id}")
    public AccessKeyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAccessKeyRequest request,
                                    Authentication authentication) {
        return accessKeyService.update(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    public AccessKeyResponse updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateAccessKeyStatusRequest request,
                                          Authentication authentication) {
        return accessKeyService.updateStatus(id, request.status(), authentication.getName());
    }

    @PostMapping("/{id}/rotate-secret")
    public AccessKeyCredentialsResponse rotateSecret(@PathVariable Long id, Authentication authentication) {
        return accessKeyService.rotateSecret(id, authentication.getName());
    }

    /** Tops up the key's prepaid balance. */
    @PostMapping("/{id}/balance/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public BalanceTransactionResponse credit(@PathVariable Long id, @Valid @RequestBody CreditBalanceRequest request,
                                             Authentication authentication) {
        return balanceService.credit(id, request.amount(), request.reason(), authentication.getName());
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return balanceService.getBalance(id);
    }

    @GetMapping("/{id}/balance/transactions")
    public PageResponse<BalanceTransactionResponse> transactions(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return balanceService.history(id, page, size);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        accessKeyService.delete(id, authentication.getName());
    }
}
