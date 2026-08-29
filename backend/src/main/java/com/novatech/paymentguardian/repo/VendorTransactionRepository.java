package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.VendorTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VendorTransactionRepository extends JpaRepository<VendorTransaction, Long> {
    List<VendorTransaction> findByVendorIdOrderByTimestampDesc(Long vendorId);
}
