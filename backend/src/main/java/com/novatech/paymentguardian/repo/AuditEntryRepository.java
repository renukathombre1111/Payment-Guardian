package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByCaseIdOrderByTimestampDesc(Long caseId);
}
