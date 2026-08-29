package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.Invoice;
import com.novatech.paymentguardian.domain.Vendor;
import com.novatech.paymentguardian.domain.VendorBankAccount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyEngine {

    private final PolicyProperties policy;

    public PolicyEngine(PolicyProperties policy) {
        this.policy = policy;
    }

    public record PolicyViolation(String code, String message, String severity) {}

    public record PolicyResult(List<PolicyViolation> violations, boolean invoicePresent) {}

    public PolicyResult evaluate(
            Vendor vendor,
            BigDecimal amount,
            List<VendorBankAccount> accounts,
            List<Invoice> invoices,
            BigDecimal currentCash,
            BigDecimal upcomingOutflows,
            Instant evaluationTime
    ) {
        List<PolicyViolation> violations = new ArrayList<>();
        Instant now = evaluationTime == null ? Instant.now() : evaluationTime;

        Invoice matching = invoices.stream()
                .filter(i -> i.getAmount().compareTo(amount) == 0 && "OPEN".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElse(null);
        boolean invoicePresent = matching != null;

        if (policy.isInvoiceRequired() && !invoicePresent) {
            violations.add(new PolicyViolation(
                    "INVOICE_REQUIRED",
                    "Company policy requires a matching open invoice before payment",
                    "CRITICAL"));
        }

        if (amount.compareTo(policy.getMaxPaymentWithoutApproval()) > 0) {
            violations.add(new PolicyViolation(
                    "MAX_WITHOUT_APPROVAL",
                    "Amount exceeds auto-approval limit of ₹" + policy.getMaxPaymentWithoutApproval(),
                    "HIGH"));
        }

        VendorBankAccount active = accounts.stream().filter(VendorBankAccount::isActive).findFirst().orElse(null);
        if (active != null && active.getCreatedAt() != null) {
            Duration age = Duration.between(active.getCreatedAt(), now);
            if (age.toHours() < policy.getBankCoolingHours()) {
                violations.add(new PolicyViolation(
                        "BANK_COOLING",
                        "Bank account changed " + age.toHours() + "h ago; cooling period is "
                                + policy.getBankCoolingHours() + "h",
                        "HIGH"));
            }
        }

        long vendorAgeDays = vendor.getCreatedAt() == null ? 365
                : Duration.between(vendor.getCreatedAt(), now).toDays();
        if (vendorAgeDays < 90 && amount.compareTo(policy.getNewVendorMaxPayment()) > 0) {
            violations.add(new PolicyViolation(
                    "NEW_VENDOR_LIMIT",
                    "Vendor is " + vendorAgeDays + " days old; max payment is ₹"
                            + policy.getNewVendorMaxPayment(),
                    "HIGH"));
        }

        BigDecimal projected = currentCash.subtract(amount).subtract(upcomingOutflows);
        if (projected.compareTo(policy.getMinCashBuffer()) < 0) {
            violations.add(new PolicyViolation(
                    "MIN_CASH_BUFFER",
                    "Projected cash ₹" + projected + " would fall below buffer ₹"
                            + policy.getMinCashBuffer(),
                    "MEDIUM"));
        }

        return new PolicyResult(violations, invoicePresent);
    }

    public PolicyProperties getPolicy() {
        return policy;
    }
}
