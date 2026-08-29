package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.Vendor;
import com.novatech.paymentguardian.domain.VendorBankAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {

    private PolicyEngine engine;
    private Instant now;

    @BeforeEach
    void setUp() {
        PolicyProperties props = new PolicyProperties();
        props.setMaxPaymentWithoutApproval(new BigDecimal("500000"));
        props.setBankCoolingHours(24);
        props.setNewVendorMaxPayment(new BigDecimal("200000"));
        props.setMinCashBuffer(new BigDecimal("2500000"));
        props.setInvoiceRequired(true);
        engine = new PolicyEngine(props);
        now = Instant.parse("2026-08-27T16:00:00Z");
    }

    @Test
    void flagsMissingInvoice() {
        Vendor vendor = Vendor.builder().name("Test").createdAt(now.minus(200, ChronoUnit.DAYS)).build();
        var result = engine.evaluate(vendor, new BigDecimal("100000"), List.of(), List.of(),
                new BigDecimal("5000000"), BigDecimal.ZERO, now);
        assertFalse(result.invoicePresent());
        assertTrue(result.violations().stream().anyMatch(v -> "INVOICE_REQUIRED".equals(v.code())));
    }

    @Test
    void flagsBankCoolingPeriod() {
        Vendor vendor = Vendor.builder().name("Test").createdAt(now.minus(200, ChronoUnit.DAYS)).build();
        VendorBankAccount acct = VendorBankAccount.builder()
                .accountNumber("XXXX1234")
                .createdAt(now.minus(6, ChronoUnit.HOURS))
                .active(true)
                .build();
        var result = engine.evaluate(vendor, new BigDecimal("100000"), List.of(acct), List.of(),
                new BigDecimal("5000000"), BigDecimal.ZERO, now);
        assertTrue(result.violations().stream().anyMatch(v -> "BANK_COOLING".equals(v.code())));
    }
}
