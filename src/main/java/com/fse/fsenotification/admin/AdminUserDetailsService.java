package com.fse.fsenotification.admin;

import com.fse.fsenotification.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads admin users from the database for login and for every Bearer-token request on /api/v1/admin/**.
 * Admins who must change their password get only {@link Roles#PASSWORD_CHANGE_REQUIRED}, not ADMIN.
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        String role = admin.isMustChangePassword() ? Roles.PASSWORD_CHANGE_REQUIRED : Roles.ADMIN;
        return new AdminUserDetails(admin.getUsername(), admin.getPasswordHash(), admin.isEnabled(),
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), admin.getPasswordChangedAt());
    }
}
