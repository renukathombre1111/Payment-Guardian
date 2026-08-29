package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskEngineTest {

    private RiskEngine engine;
    private Instant now;

    @BeforeEach
    void setUp() {
        PolicyProperties props = new PolicyProperties();
        props.setMinCashBuffer(new BigDecimal("2500000"));
        props.setBankCoolingHours(24);
        engine = new RiskEngine(props);
        now = Instant.parse("2026-08-27T16:00:00Z");
    }

    @Test
    void producesTwelveSignals() {
        Vendor vendor = Vendor.builder().name("ABC").riskScore(42)
                .createdAt(now.minus(400, ChronoUnit.DAYS)).build();
        Invoice inv = Invoice.builder().invoiceNumber("INV-1").amount(new BigDecimal("1850000")).status("OPEN").build();
        VendorBankAccount acct = VendorBankAccount.builder()
                .accountNumber("XXXX8837").createdAt(now.minus(19, ChronoUnit.HOURS)).active(true).build();
        List<VendorTransaction> history = List.of(
                tx(new BigDecimal("400000"), now.minus(30, ChronoUnit.DAYS)),
                tx(new BigDecimal("410000"), now.minus(60, ChronoUnit.DAYS)),
                tx(new BigDecimal("420000"), now.minus(90, ChronoUnit.DAYS))
        );
        PolicyEngine.PolicyResult policy = new PolicyEngine.PolicyResult(List.of(), true);

        RiskEngine.RiskResult result = engine.score(new RiskEngine.RiskContext(
                new BigDecimal("1850000"), LocalDate.of(2026, 8, 27),
                vendor, history, List.of(acct), List.of(inv), inv,
                new BigDecimal("12000000"), new BigDecimal("6220000"),
                policy, false, now));

        assertEquals(12, result.signals().size());
        assertTrue(result.totalScore() >= 31, "ABC scenario should be REVIEW or higher");
        assertEquals("REVIEW", RiskEngine.actionFor(result.totalScore(), policy));
    }

    @Test
    void bandMapping() {
        assertEquals("LOW", RiskEngine.bandFor(20));
        assertEquals("MEDIUM", RiskEngine.bandFor(45));
        assertEquals("HIGH", RiskEngine.bandFor(70));
        assertEquals("CRITICAL", RiskEngine.bandFor(90));
    }

    @Test
    void blockOnCriticalPolicy() {
        PolicyEngine.PolicyResult policy = new PolicyEngine.PolicyResult(
                List.of(new PolicyEngine.PolicyViolation("INVOICE_REQUIRED", "missing", "CRITICAL")),
                false);
        assertEquals("BLOCK", RiskEngine.actionFor(10, policy));
    }

    private VendorTransaction tx(BigDecimal amount, Instant ts) {
        return VendorTransaction.builder().amount(amount).status("PAID").timestamp(ts).build();
    }
}
