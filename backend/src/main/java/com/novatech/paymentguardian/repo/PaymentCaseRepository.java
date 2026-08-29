package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.PaymentCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentCaseRepository extends JpaRepository<PaymentCase, Long> {
    List<PaymentCase> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
    long countByDecision(String decision);
}
