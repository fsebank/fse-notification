package com.fse.fsenotification.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Seeds the default admin account on startup when no admin with the configured username exists,
 * regardless of how many other admins there are.
 * <p>
 * Configured by app.security.default-admin.*. If no password is configured, a random one is generated and
 * logged once, and the admin must change it at first login. An existing default admin (even a disabled one)
 * is never modified, so its password is not reset on restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminSeeder implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-admin.username:admin}")
    private String username;

    @Value("${app.security.default-admin.password:}")
    private String password;

    @Value("${app.security.default-admin.full-name:Default Administrator}")
    private String fullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUserRepository.existsByUsernameIgnoreCase(username.trim())) {
            return;
        }
        boolean generated = !StringUtils.hasText(password);
        String rawPassword = generated ? generatePassword() : password;

        AdminUser admin = new AdminUser();
        admin.setUsername(username.trim());
        admin.changePassword(passwordEncoder.encode(rawPassword), generated);
        admin.setFullName(fullName);
        admin.setEnabled(true);
        admin.setCreatedBy("system");
        adminUserRepository.save(admin);

        if (generated) {
            log.warn("Default admin '{}' created with generated password: {}  (shown once; must be changed at "
                    + "first login)", admin.getUsername(), rawPassword);
        } else {
            log.info("Default admin '{}' created", admin.getUsername());
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
