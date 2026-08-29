package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByVendorId(Long vendorId);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByVendorIdAndAmount(Long vendorId, java.math.BigDecimal amount);
}
