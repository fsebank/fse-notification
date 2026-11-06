package com.fse.fsenotification.admin;

import com.fse.fsenotification.admin.AdminUserDtos.AdminResponse;
import com.fse.fsenotification.admin.AdminUserDtos.CreateAdminRequest;
import com.fse.fsenotification.admin.AdminUserDtos.ResetPasswordRequest;
import com.fse.fsenotification.admin.AdminUserDtos.UpdateAdminStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@Valid @RequestBody CreateAdminRequest request, Authentication authentication) {
        return adminUserService.create(request, authentication.getName());
    }

    @GetMapping
    public List<AdminResponse> list() {
        return adminUserService.findAll();
    }

    @PatchMapping("/{id}/status")
    public AdminResponse updateStatus(@PathVariable Long id, @RequestBody UpdateAdminStatusRequest request,
                                      Authentication authentication) {
        return adminUserService.updateStatus(id, request.enabled(), authentication.getName());
    }

    @PutMapping("/{id}/password")
    public AdminResponse resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request,
                                       Authentication authentication) {
        return adminUserService.resetPassword(id, request.newPassword(), authentication.getName());
    }
}
