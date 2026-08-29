package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.VendorBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VendorBankAccountRepository extends JpaRepository<VendorBankAccount, Long> {
    List<VendorBankAccount> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
}
