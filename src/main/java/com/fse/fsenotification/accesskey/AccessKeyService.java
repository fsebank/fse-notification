package com.fse.fsenotification.accesskey;

import com.fse.fsenotification.accesskey.AccessKeyDtos.AccessKeyCredentialsResponse;
import com.fse.fsenotification.accesskey.AccessKeyDtos.AccessKeyResponse;
import com.fse.fsenotification.accesskey.AccessKeyDtos.CreateAccessKeyRequest;
import com.fse.fsenotification.accesskey.AccessKeyDtos.UpdateAccessKeyRequest;
import com.fse.fsenotification.common.BadRequestException;
import com.fse.fsenotification.common.ResourceNotFoundException;
import com.fse.fsenotification.security.PartnerAuthenticationException;
import com.fse.fsenotification.security.PartnerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessKeyService {

    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);

    private final PartnerAccessKeyRepository repository;
    private final CredentialGenerator credentialGenerator;

    // ---------------------------------------------------------------- admin operations

    @Transactional
    public AccessKeyCredentialsResponse create(CreateAccessKeyRequest request, String createdBy) {
        String clientId;
        do {
            clientId = credentialGenerator.newClientId();
        } while (repository.existsByClientId(clientId));

        String rawSecret = credentialGenerator.newClientSecret();

        PartnerAccessKey key = new PartnerAccessKey();
        key.setClientName(request.clientName().trim());
        key.setClientId(clientId);
        key.setClientSecretHash(credentialGenerator.hashSecret(rawSecret));
        key.setWhitelistedIps(normalizeIps(request.whitelistedIps()));
        key.setStatus(AccessKeyStatus.ACTIVE);
        key.setCreatedBy(createdBy);

        PartnerAccessKey saved = repository.save(key);
        log.info("Access key {} created for '{}' by {}", saved.getClientId(), saved.getClientName(), createdBy);
        return AccessKeyCredentialsResponse.from(saved, rawSecret);
    }

    @Transactional(readOnly = true)
    public List<AccessKeyResponse> findAll() {
        return repository.findAll().stream().map(AccessKeyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccessKeyResponse findById(Long id) {
        return AccessKeyResponse.from(load(id));
    }

    @Transactional
    public AccessKeyResponse update(Long id, UpdateAccessKeyRequest request, String actor) {
        PartnerAccessKey key = load(id);
        ensureNotRevoked(key);
        if (StringUtils.hasText(request.clientName())) {
            key.setClientName(request.clientName().trim());
        }
        if (request.whitelistedIps() != null) {
            key.getWhitelistedIps().clear();
            key.getWhitelistedIps().addAll(normalizeIps(request.whitelistedIps()));
        }
        log.info("Access key {} updated by {}", key.getClientId(), actor);
        return AccessKeyResponse.from(key);
    }

    @Transactional
    public AccessKeyResponse updateStatus(Long id, AccessKeyStatus status, String actor) {
        PartnerAccessKey key = load(id);
        ensureNotRevoked(key);
        key.setStatus(status);
        log.info("Access key {} status set to {} by {}", key.getClientId(), status, actor);
        return AccessKeyResponse.from(key);
    }

    /** Issues a new secret; the previous secret stops working immediately. */
    @Transactional
    public AccessKeyCredentialsResponse rotateSecret(Long id, String actor) {
        PartnerAccessKey key = load(id);
        ensureNotRevoked(key);
        String rawSecret = credentialGenerator.newClientSecret();
        key.setClientSecretHash(credentialGenerator.hashSecret(rawSecret));
        log.info("Access key {} secret rotated by {}", key.getClientId(), actor);
        return AccessKeyCredentialsResponse.from(key, rawSecret);
    }

    /**
     * Permanently removes an unused key and its IP whitelist. Keys with messages or balance history are kept for
     * audit and must be revoked instead.
     */
    @Transactional
    public void delete(Long id, String actor) {
        PartnerAccessKey key = load(id);
        if (repository.hasActivity(id)) {
            throw new BadRequestException(
                    "Access key has messages or balance history and cannot be deleted; set its status to REVOKED instead");
        }
        repository.delete(key);
        log.info("Access key {} ('{}') deleted by {}", key.getClientId(), key.getClientName(), actor);
    }

    // ---------------------------------------------------------------- per-request validation

    /**
     * Validates partner credentials for a single request. Called by the security filter on every request to a
     * partner-protected resource.
     */
    @Transactional
    public PartnerPrincipal validate(String clientId, String clientSecret, String clientIp) {
        PartnerAccessKey key = repository.findByClientId(clientId).orElse(null);
        if (key == null) {
            credentialGenerator.hashSecret(clientSecret); // keep timing similar to the found-key path
            throw new PartnerAuthenticationException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }
        if (!credentialGenerator.matches(clientSecret, key.getClientSecretHash())) {
            log.warn("Invalid secret presented for client {} from {}", clientId, clientIp);
            throw new PartnerAuthenticationException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }
        if (key.getStatus() != AccessKeyStatus.ACTIVE) {
            throw new PartnerAuthenticationException(HttpStatus.UNAUTHORIZED,
                    "Access key is " + key.getStatus().name().toLowerCase());
        }
        Instant now = Instant.now();
        if (!IpWhitelist.isAllowed(key.getWhitelistedIps(), clientIp)) {
            log.warn("Client {} rejected: IP {} not whitelisted", clientId, clientIp);
            throw new PartnerAuthenticationException(HttpStatus.FORBIDDEN, "Request IP address is not whitelisted");
        }

        repository.touchLastUsed(key.getId(), now, now.minus(LAST_USED_RESOLUTION));
        return new PartnerPrincipal(key.getId(), key.getClientId(), key.getClientName());
    }

    // ---------------------------------------------------------------- helpers

    private PartnerAccessKey load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Access key not found"));
    }

    private static void ensureNotRevoked(PartnerAccessKey key) {
        if (key.getStatus() == AccessKeyStatus.REVOKED) {
            throw new BadRequestException("Access key is revoked and can no longer be modified");
        }
    }

    private static Set<String> normalizeIps(Set<String> ips) {
        Set<String> result = new LinkedHashSet<>();
        if (ips == null) {
            return result;
        }
        for (String ip : ips) {
            String trimmed = ip == null ? "" : ip.trim();
            if (!IpWhitelist.isValidEntry(trimmed)) {
                throw new BadRequestException("Invalid IP address or CIDR: " + ip);
            }
            result.add(trimmed);
        }
        return result;
    }
}
