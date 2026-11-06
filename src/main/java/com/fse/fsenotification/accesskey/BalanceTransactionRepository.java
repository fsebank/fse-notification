package com.fse.fsenotification.accesskey;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    Page<BalanceTransaction> findByAccessKeyIdOrderByCreatedAtDescIdDesc(Long accessKeyId, Pageable pageable);

    boolean existsByAccessKeyId(Long accessKeyId);
}
