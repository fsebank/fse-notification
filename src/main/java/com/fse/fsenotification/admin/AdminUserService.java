package com.fse.fsenotification.admin;

import com.fse.fsenotification.admin.AdminUserDtos.AdminResponse;
import com.fse.fsenotification.admin.AdminUserDtos.CreateAdminRequest;
import com.fse.fsenotification.common.BadRequestException;
import com.fse.fsenotification.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    /** The creator chooses the initial password, so the new admin must change it at first login. */
    @Transactional
    public AdminResponse create(CreateAdminRequest request, String createdBy) {
        if (adminUserRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BadRequestException("Username already exists");
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(request.username());
        admin.changePassword(passwordEncoder.encode(request.password()), true);
        admin.setFullName(request.fullName());
        admin.setEnabled(true);
        admin.setCreatedBy(createdBy);
        return AdminResponse.from(adminUserRepository.save(admin));
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> findAll() {
        return adminUserRepository.findAll().stream().map(AdminResponse::from).toList();
    }

    @Transactional
    public AdminResponse updateStatus(Long id, boolean enabled, String actor) {
        AdminUser admin = load(id);
        if (!enabled && admin.getUsername().equalsIgnoreCase(actor)) {
            throw new BadRequestException("You cannot disable your own account");
        }
        admin.setEnabled(enabled);
        return AdminResponse.from(admin);
    }

    /** Changes the caller's own password and clears any pending forced change. Existing tokens stop working. */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new IncorrectPasswordException();
        }
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        admin.changePassword(passwordEncoder.encode(newPassword), false);
        log.info("Admin '{}' changed their password", admin.getUsername());
    }

    /** Sets another admin's password; they must change it at next login. Their existing tokens stop working. */
    @Transactional
    public AdminResponse resetPassword(Long id, String newPassword, String actor) {
        AdminUser admin = load(id);
        if (admin.getUsername().equalsIgnoreCase(actor)) {
            throw new BadRequestException("Use the change-password endpoint to change your own password");
        }
        admin.changePassword(passwordEncoder.encode(newPassword), true);
        log.info("Password for admin '{}' reset by {}", admin.getUsername(), actor);
        return AdminResponse.from(admin);
    }

    private AdminUser load(Long id) {
        return adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
    }
}
