package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
}
