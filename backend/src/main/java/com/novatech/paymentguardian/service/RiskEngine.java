package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RiskEngine {

    private final PolicyProperties policy;

    public RiskEngine(PolicyProperties policy) {
        this.policy = policy;
    }

    public record RiskSignal(
            String name,
            String severity,
            String evidence,
            int contribution,
            String explanation
    ) {}

    public record RiskResult(
            int totalScore,
            String riskBand,
            String recommendedAction,
            BigDecimal historicalAverage,
            BigDecimal multiplier,
            List<RiskSignal> signals
    ) {}

    public RiskResult score(RiskContext ctx) {
        List<RiskSignal> signals = new ArrayList<>();
        int score = 0;

        List<VendorTransaction> paid = ctx.history().stream()
                .filter(t -> "PAID".equalsIgnoreCase(t.getStatus()) || "SETTLED".equalsIgnoreCase(t.getStatus()))
                .toList();

        BigDecimal avg = averagePaid(paid);
        BigDecimal multiplier = avg.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : ctx.proposedAmount().divide(avg, 3, RoundingMode.HALF_UP);

        score += addSignal(signals, amountSignal(ctx.proposedAmount(), avg, multiplier));
        score += addSignal(signals, vendorSignal(ctx.vendor(), paid));
        score += addSignal(signals, bankAccountChangeSignal(ctx.accounts(), paid, ctx.evaluationTime()));
        score += addSignal(signals, invoiceSignal(ctx.matchingInvoice(), ctx.invoices(), ctx.proposedAmount()));
        score += addSignal(signals, duplicateInvoiceSignal(ctx.invoices(), ctx.matchingInvoice()));
        score += addSignal(signals, paymentFrequencySignal(paid, ctx.proposedDate()));
        score += addSignal(signals, unusualTimingSignal(ctx.proposedDate()));
        score += addSignal(signals, vendorAgeSignal(ctx.vendor(), ctx.evaluationTime()));
        score += addSignal(signals, cashFlowPressureSignal(ctx.currentCash(), ctx.proposedAmount(), ctx.upcomingOutflows()));
        score += addSignal(signals, historicalAnomalySignal(paid, ctx.proposedAmount(), multiplier));
        score += addSignal(signals, policyViolationSignal(ctx.policyResult()));
        score += addSignal(signals, splitPaymentSignal(ctx.splitPayment(), ctx.proposedAmount()));

        score = Math.min(100, score);
        String band = bandFor(score);
        String action = actionFor(score, ctx.policyResult());
        return new RiskResult(score, band, action, avg, multiplier, signals);
    }

    public static String bandFor(int score) {
        if (score <= 30) return "LOW";
        if (score <= 60) return "MEDIUM";
        if (score <= 80) return "HIGH";
        return "CRITICAL";
    }

    public static String actionFor(int score, PolicyEngine.PolicyResult policyResult) {
        boolean criticalPolicy = policyResult.violations().stream()
                .anyMatch(v -> "CRITICAL".equals(v.severity()));
        if (criticalPolicy || score >= 81) return "BLOCK";
        if (score >= 31) return "REVIEW";
        return "APPROVE";
    }

    public record RiskContext(
            BigDecimal proposedAmount,
            LocalDate proposedDate,
            Vendor vendor,
            List<VendorTransaction> history,
            List<VendorBankAccount> accounts,
            List<Invoice> invoices,
            Invoice matchingInvoice,
            BigDecimal currentCash,
            BigDecimal upcomingOutflows,
            PolicyEngine.PolicyResult policyResult,
            boolean splitPayment,
            Instant evaluationTime
    ) {}

    private int addSignal(List<RiskSignal> signals, RiskSignal signal) {
        signals.add(signal);
        return signal.contribution();
    }

    private BigDecimal averagePaid(List<VendorTransaction> paid) {
        if (paid.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = paid.stream().map(VendorTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP);
    }

    private RiskSignal amountSignal(BigDecimal amount, BigDecimal avg, BigDecimal multiplier) {
        if (multiplier.compareTo(BigDecimal.valueOf(4)) > 0) {
            return new RiskSignal("amount", "CRITICAL",
                    "Proposed ₹" + amount + " vs avg ₹" + avg + " (" + multiplier + "×)",
                    20, "Payment far exceeds historical vendor amounts");
        }
        if (multiplier.compareTo(BigDecimal.valueOf(2)) > 0) {
            return new RiskSignal("amount", "HIGH",
                    "Proposed ₹" + amount + " vs avg ₹" + avg + " (" + multiplier + "×)",
                    12, "Payment significantly above historical average");
        }
        if (multiplier.compareTo(BigDecimal.valueOf(1.5)) > 0) {
            return new RiskSignal("amount", "MEDIUM",
                    "Proposed ₹" + amount + " vs avg ₹" + avg,
                    6, "Payment moderately above historical average");
        }
        return new RiskSignal("amount", "LOW",
                "Proposed ₹" + amount + " vs avg ₹" + avg, 0, "Amount consistent with history");
    }

    private RiskSignal vendorSignal(Vendor vendor, List<VendorTransaction> paid) {
        int vendorRisk = vendor.getRiskScore() == null ? 0 : vendor.getRiskScore();
        if (vendorRisk >= 70) {
            return new RiskSignal("vendor", "CRITICAL",
                    vendor.getName() + " risk score " + vendorRisk, 15,
                    "Vendor flagged as high-risk in master data");
        }
        if (vendorRisk >= 40) {
            return new RiskSignal("vendor", "MEDIUM",
                    vendor.getName() + " risk score " + vendorRisk, 8,
                    "Vendor has elevated baseline risk");
        }
        if (paid.isEmpty()) {
            return new RiskSignal("vendor", "MEDIUM",
                    vendor.getName() + " — no payment history", 10,
                    "First-time payment to vendor");
        }
        return new RiskSignal("vendor", "LOW",
                vendor.getName() + " — " + paid.size() + " prior payments", 0,
                "Established vendor relationship");
    }

    private RiskSignal bankAccountChangeSignal(
            List<VendorBankAccount> accounts,
            List<VendorTransaction> paid,
            Instant now
    ) {
        VendorBankAccount current = accounts.stream().filter(VendorBankAccount::isActive).findFirst().orElse(null);
        VendorBankAccount previous = accounts.stream().filter(a -> !a.isActive()).findFirst().orElse(null);
        if (current == null) {
            return new RiskSignal("bank-account-change", "HIGH", "No active bank account on file", 15,
                    "Cannot verify destination account");
        }
        if (current.getCreatedAt() == null) {
            return new RiskSignal("bank-account-change", "LOW", current.getAccountNumber(), 0,
                    "Bank account on file");
        }
        Duration age = Duration.between(current.getCreatedAt(), now);
        String prev = previous == null ? "unknown" : previous.getAccountNumber();
        if (age.toHours() < policy.getBankCoolingHours()) {
            int pts = age.toHours() < 12 ? 18 : 12;
            return new RiskSignal("bank-account-change", "HIGH",
                    prev + " → " + current.getAccountNumber() + " (" + age.toHours() + "h ago)",
                    pts, "Recent bank account change within cooling period");
        }
        boolean paidToAccount = paid.stream().anyMatch(t ->
                t.getReference() != null && current.getAccountNumber() != null
                        && t.getReference().contains(current.getAccountNumber().substring(
                        Math.max(0, current.getAccountNumber().length() - 4))));
        if (!paidToAccount && previous != null) {
            return new RiskSignal("bank-account-change", "MEDIUM",
                    "No settled payments to " + current.getAccountNumber(), 8,
                    "New account never received funds before");
        }
        return new RiskSignal("bank-account-change", "LOW",
                current.getAccountNumber() + " verified in history", 0,
                "Bank account stable or previously used");
    }

    private RiskSignal invoiceSignal(Invoice matching, List<Invoice> invoices, BigDecimal amount) {
        if (matching != null) {
            return new RiskSignal("invoice", "LOW",
                    matching.getInvoiceNumber() + " matches ₹" + amount, 0,
                    "Matching open invoice found");
        }
        long openCount = invoices.stream().filter(i -> "OPEN".equalsIgnoreCase(i.getStatus())).count();
        return new RiskSignal("invoice", "CRITICAL",
                "No open invoice for ₹" + amount + " (" + openCount + " open invoices)", 25,
                "Missing required invoice documentation");
    }

    private RiskSignal duplicateInvoiceSignal(List<Invoice> invoices, Invoice matching) {
        if (matching == null) {
            return new RiskSignal("duplicate-invoice", "LOW", "N/A — no matching invoice", 0,
                    "Duplicate check skipped");
        }
        long dupes = invoices.stream()
                .filter(i -> Objects.equals(i.getInvoiceNumber(), matching.getInvoiceNumber()))
                .count();
        long amountDupes = invoices.stream()
                .filter(i -> i.getAmount().compareTo(matching.getAmount()) == 0
                        && "PAID".equalsIgnoreCase(i.getStatus()))
                .count();
        if (dupes > 1 || amountDupes > 0) {
            return new RiskSignal("duplicate-invoice", "HIGH",
                    "Invoice " + matching.getInvoiceNumber() + " may be duplicate", 15,
                    "Same invoice or amount already paid");
        }
        return new RiskSignal("duplicate-invoice", "LOW",
                matching.getInvoiceNumber() + " unique", 0, "No duplicate invoice detected");
    }

    private RiskSignal paymentFrequencySignal(List<VendorTransaction> paid, LocalDate proposedDate) {
        if (paid.size() < 2) {
            return new RiskSignal("payment-frequency", "LOW", "Insufficient history", 0,
                    "Not enough payments to assess frequency");
        }
        List<VendorTransaction> recent = paid.stream()
                .sorted(Comparator.comparing(VendorTransaction::getTimestamp).reversed())
                .limit(3)
                .toList();
        long daysBetween = 0;
        if (recent.size() >= 2) {
            daysBetween = ChronoUnit.DAYS.between(
                    recent.get(1).getTimestamp(), recent.get(0).getTimestamp());
        }
        long daysSinceLast = recent.isEmpty() ? 999
                : ChronoUnit.DAYS.between(recent.get(0).getTimestamp(),
                proposedDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        if (daysSinceLast < 7 && daysBetween > 14) {
            return new RiskSignal("payment-frequency", "HIGH",
                    "Last payment " + daysSinceLast + " days ago (typical gap " + daysBetween + "d)", 10,
                    "Unusually frequent payment vs vendor pattern");
        }
        return new RiskSignal("payment-frequency", "LOW",
                "Typical cadence (" + daysBetween + "d between payments)", 0,
                "Payment frequency normal");
    }

    private RiskSignal unusualTimingSignal(LocalDate proposedDate) {
        int dow = proposedDate.getDayOfWeek().getValue();
        if (dow >= 6) {
            return new RiskSignal("unusual-timing", "MEDIUM",
                    "Proposed on weekend (" + proposedDate + ")", 5,
                    "Weekend payments are atypical for AP");
        }
        if (proposedDate.getDayOfMonth() >= 28) {
            return new RiskSignal("unusual-timing", "LOW",
                    "End-of-month payment (" + proposedDate + ")", 3,
                    "Month-end timing — monitor cash");
        }
        return new RiskSignal("unusual-timing", "LOW", proposedDate.toString(), 0,
                "Payment timing within normal business hours");
    }

    private RiskSignal vendorAgeSignal(Vendor vendor, Instant now) {
        if (vendor.getCreatedAt() == null) {
            return new RiskSignal("vendor-age", "LOW", "Unknown vendor age", 0, "Vendor age not recorded");
        }
        long days = Duration.between(vendor.getCreatedAt(), now).toDays();
        if (days < 30) {
            return new RiskSignal("vendor-age", "HIGH", days + " days since onboarding", 12,
                    "Very new vendor — limited track record");
        }
        if (days < 90) {
            return new RiskSignal("vendor-age", "MEDIUM", days + " days since onboarding", 6,
                    "Relatively new vendor");
        }
        return new RiskSignal("vendor-age", "LOW", days + " days established", 0,
                "Mature vendor relationship");
    }

    private RiskSignal cashFlowPressureSignal(BigDecimal cash, BigDecimal amount, BigDecimal outflows) {
        BigDecimal projected = cash.subtract(amount).subtract(outflows);
        BigDecimal buffer = policy.getMinCashBuffer();
        if (projected.compareTo(buffer.multiply(new BigDecimal("0.5"))) < 0) {
            return new RiskSignal("cash-flow-pressure", "CRITICAL",
                    "Projected ₹" + projected + " vs buffer ₹" + buffer, 18,
                    "Severe cash pressure after payment");
        }
        if (projected.compareTo(buffer) < 0) {
            return new RiskSignal("cash-flow-pressure", "HIGH",
                    "Projected ₹" + projected + " below buffer ₹" + buffer, 10,
                    "Payment would breach minimum cash buffer");
        }
        return new RiskSignal("cash-flow-pressure", "LOW",
                "Projected ₹" + projected + " above buffer", 0,
                "Adequate liquidity after payment");
    }

    private RiskSignal historicalAnomalySignal(
            List<VendorTransaction> paid,
            BigDecimal amount,
            BigDecimal multiplier
    ) {
        if (paid.size() < 3) {
            return new RiskSignal("historical-anomaly", "LOW", "Limited history", 0,
                    "Insufficient data for anomaly detection");
        }
        BigDecimal max = paid.stream().map(VendorTransaction::getAmount).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (amount.compareTo(max.multiply(new BigDecimal("2"))) > 0) {
            return new RiskSignal("historical-anomaly", "HIGH",
                    "₹" + amount + " exceeds 2× prior max ₹" + max, 10,
                    "Statistical outlier vs vendor payment history");
        }
        if (multiplier.compareTo(new BigDecimal("3")) > 0) {
            return new RiskSignal("historical-anomaly", "MEDIUM",
                    multiplier + "× average with " + paid.size() + " prior payments", 6,
                    "Sudden spike despite stable vendor history");
        }
        return new RiskSignal("historical-anomaly", "LOW",
                "Within historical range (max ₹" + max + ")", 0,
                "No statistical anomaly");
    }

    private RiskSignal policyViolationSignal(PolicyEngine.PolicyResult policyResult) {
        if (policyResult.violations().isEmpty()) {
            return new RiskSignal("policy-violation", "LOW", "All policies satisfied", 0,
                    "No company policy violations");
        }
        int pts = Math.min(20, policyResult.violations().size() * 8);
        String evidence = policyResult.violations().stream()
                .map(PolicyEngine.PolicyViolation::code)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String severity = policyResult.violations().stream()
                .anyMatch(v -> "CRITICAL".equals(v.severity())) ? "CRITICAL" : "HIGH";
        return new RiskSignal("policy-violation", severity,
                evidence + " (" + policyResult.violations().size() + " violations)", pts,
                "Company payment policy rules triggered");
    }

    private RiskSignal splitPaymentSignal(boolean splitPayment, BigDecimal amount) {
        if (!splitPayment) {
            return new RiskSignal("split-payment", "LOW", "Full payment proposed", 0,
                    "Not a split-payment attempt");
        }
        if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            return new RiskSignal("split-payment", "MEDIUM",
                    "Split proposed for ₹" + amount, 5,
                    "Large payment split — verify not circumventing limits");
        }
        return new RiskSignal("split-payment", "LOW", "Split for ₹" + amount, 0,
                "Split payment within normal range");
    }
}
