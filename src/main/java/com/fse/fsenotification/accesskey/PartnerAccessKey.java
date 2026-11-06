package com.fse.fsenotification.accesskey;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "partner_access_key")
@Getter
@Setter
@NoArgsConstructor
public class PartnerAccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "client_id", nullable = false, unique = true, length = 64, updatable = false)
    private String clientId;

    /** SHA-256 hex digest of the client secret. The raw secret is only ever returned once, at creation/rotation. */
    @Column(name = "client_secret_hash", nullable = false, length = 128)
    private String clientSecretHash;

    /** Optional whitelist of IPs or CIDR ranges. Empty means requests from any IP are accepted. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "partner_access_key_ip", joinColumns = @JoinColumn(name = "access_key_id"))
    @Column(name = "ip_address", nullable = false, length = 64)
    private Set<String> whitelistedIps = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessKeyStatus status = AccessKeyStatus.ACTIVE;

    /**
     * Prepaid balance. Never written through the entity (insertable/updatable = false) so a stale entity save
     * cannot overwrite it; changed only by atomic SQL in {@link PartnerAccessKeyRepository} via {@link BalanceService}.
     */
    @Setter(lombok.AccessLevel.NONE)
    @Column(nullable = false, precision = 19, scale = 2, insertable = false, updatable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
