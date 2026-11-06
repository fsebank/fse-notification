package com.fse.fsenotification.security;

import com.fse.fsenotification.accesskey.AccessKeyService;
import com.fse.fsenotification.admin.AdminUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Three stateless filter chains, evaluated in order:
 * <ol>
 *   <li>{@code /api/v1/admin/**} - admin users, Bearer JWT issued by {@code POST /api/v1/admin/auth/login},
 *       ROLE_ADMIN. The login endpoint itself is open; change-password is also allowed for admins who
 *       still have ROLE_PASSWORD_CHANGE_REQUIRED.</li>
 *   <li>{@code /api/**} - partners, validated by access key headers on every request, ROLE_PARTNER.</li>
 *   <li>everything else - denied (except the error page).</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

    public static final String ADMIN_LOGIN_PATH = "/api/v1/admin/auth/login";
    public static final String ADMIN_CHANGE_PASSWORD_PATH = "/api/v1/admin/auth/change-password";

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
    private final RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Verifies admin username/password; used by the login endpoint. */
    @Bean
    public AuthenticationManager adminAuthenticationManager(AdminUserDetailsService adminUserDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider adminAuthProvider = new DaoAuthenticationProvider(adminUserDetailsService);
        adminAuthProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(adminAuthProvider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AdminUserDetailsService adminUserDetailsService,
                                                        JwtDecoder jwtDecoder) throws Exception {
        http.securityMatcher("/api/v1/admin/**");
        applyStatelessDefaults(http);
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, ADMIN_LOGIN_PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, ADMIN_CHANGE_PASSWORD_PATH)
                        .hasAnyRole(Roles.ADMIN, Roles.PASSWORD_CHANGE_REQUIRED)
                        .anyRequest().hasRole(Roles.ADMIN))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(adminBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new AdminJwtAuthenticationConverter(adminUserDetailsService))));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain partnerSecurityFilterChain(HttpSecurity http, AccessKeyService accessKeyService)
            throws Exception {
        http.securityMatcher("/api/**");
        applyStatelessDefaults(http);
        http.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(Roles.PARTNER))
                .addFilterBefore(new PartnerAccessKeyFilter(accessKeyService, entryPoint),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        applyStatelessDefaults(http);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    /**
     * Ignores any Authorization header on the login endpoint, so a stale or expired token (e.g. inherited
     * from a Postman collection) cannot block a fresh login.
     */
    private static BearerTokenResolver adminBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> HttpMethod.POST.matches(request.getMethod())
                && ADMIN_LOGIN_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()))
                ? null
                : delegate.resolve(request);
    }

    private void applyStatelessDefaults(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
    }
}
