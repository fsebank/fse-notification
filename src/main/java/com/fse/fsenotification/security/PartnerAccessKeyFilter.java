package com.fse.fsenotification.security;

import com.fse.fsenotification.accesskey.AccessKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates partner access keys on every request to a partner-protected resource.
 * <p>
 * Partners send {@code X-Client-Id} and {@code X-Client-Secret} headers. The key must exist, the secret must
 * match, the key must be ACTIVE, and, if the key has a whitelist, the caller IP must match it.
 * <p>
 * Deliberately not a Spring bean, so Boot does not also register it as a global servlet filter.
 */
public class PartnerAccessKeyFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";
    public static final String CLIENT_SECRET_HEADER = "X-Client-Secret";

    private final AccessKeyService accessKeyService;
    private final AuthenticationEntryPoint entryPoint;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public PartnerAccessKeyFilter(AccessKeyService accessKeyService, AuthenticationEntryPoint entryPoint) {
        this.accessKeyService = accessKeyService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String clientSecret = request.getHeader(CLIENT_SECRET_HEADER);

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            entryPoint.commence(request, response, new PartnerAuthenticationException(HttpStatus.UNAUTHORIZED,
                    "Missing " + CLIENT_ID_HEADER + " or " + CLIENT_SECRET_HEADER + " header"));
            return;
        }

        PartnerPrincipal principal;
        try {
            // Remote address honours X-Forwarded-For only when server.forward-headers-strategy is configured,
            // so a client cannot spoof its IP by sending the header directly.
            principal = accessKeyService.validate(clientId.trim(), clientSecret.trim(), request.getRemoteAddr());
        } catch (PartnerAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, ex);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.PARTNER)));
        authentication.setDetails(detailsSource.buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
