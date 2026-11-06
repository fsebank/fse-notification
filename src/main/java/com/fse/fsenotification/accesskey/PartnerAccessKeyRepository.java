package com.fse.fsenotification.accesskey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface PartnerAccessKeyRepository extends JpaRepository<PartnerAccessKey, Long> {

    Optional<PartnerAccessKey> findByClientId(String clientId);

    boolean existsByClientId(String clientId);

    /** Bumps last_used_at without loading/dirty-checking the entity, throttled to avoid a write on every call. */
    @Modifying
    @Query("""
            update PartnerAccessKey k set k.lastUsedAt = :now
            where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :threshold)
            """)
    int touchLastUsed(@Param("id") Long id, @Param("now") Instant now, @Param("threshold") Instant threshold);

    /** Atomically deducts amount only if enough balance remains. Returns 0 when the balance is insufficient. */
    @Modifying(flushAutomatically = true)
    @Query(value = "update partner_access_key set balance = balance - :amount where id = :id and balance >= :amount",
            nativeQuery = true)
    int debitBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query(value = "update partner_access_key set balance = balance + :amount where id = :id", nativeQuery = true)
    int creditBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** Reads the balance straight from the database, bypassing any cached entity. */
    @Query(value = "select balance from partner_access_key where id = :id", nativeQuery = true)
    Optional<BigDecimal> findBalanceById(@Param("id") Long id);

    /** True if the key has sent messages or has balance history, which must be kept for audit. */
    @Query(value = """
            select exists(select 1 from message where access_key_id = :id)
                or exists(select 1 from balance_transaction where access_key_id = :id)
            """, nativeQuery = true)
    boolean hasActivity(@Param("id") Long id);
}
