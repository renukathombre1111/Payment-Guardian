package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.domain.*;
import com.novatech.paymentguardian.dto.ApiDtos.VendorProfileResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class VendorProfileService {

    public VendorProfileResponse build(
            Vendor vendor,
            List<VendorTransaction> history,
            List<VendorBankAccount> accounts,
            List<Invoice> invoices,
            Instant evaluationTime
    ) {
        Instant now = evaluationTime == null ? Instant.now() : evaluationTime;
        List<VendorTransaction> paid = history.stream()
                .filter(t -> "PAID".equalsIgnoreCase(t.getStatus()) || "SETTLED".equalsIgnoreCase(t.getStatus()))
                .toList();

        BigDecimal totalPaid = paid.stream().map(VendorTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = paid.isEmpty() ? BigDecimal.ZERO
                : totalPaid.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP);

        VendorBankAccount active = accounts.stream().filter(VendorBankAccount::isActive).findFirst().orElse(null);
        long accountAgeHours = active != null && active.getCreatedAt() != null
                ? Duration.between(active.getCreatedAt(), now).toHours() : -1;

        long openInvoices = invoices.stream().filter(i -> "OPEN".equalsIgnoreCase(i.getStatus())).count();
        boolean duplicateRisk = invoices.stream()
                .map(Invoice::getInvoiceNumber)
                .filter(Objects::nonNull)
                .distinct()
                .count() < invoices.size();

        long vendorAgeDays = vendor.getCreatedAt() == null ? 0
                : Duration.between(vendor.getCreatedAt(), now).toDays();

        return new VendorProfileResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getCategory(),
                vendor.getEmail(),
                vendorAgeDays,
                paid.size(),
                avg,
                totalPaid,
                active == null ? null : active.getAccountNumber(),
                accountAgeHours,
                openInvoices,
                duplicateRisk,
                vendor.getRiskScore() == null ? 0 : vendor.getRiskScore()
        );
    }
}
