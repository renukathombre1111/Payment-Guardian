package com.novatech.paymentguardian.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ApiDtos {
    public record EvaluateRequest(Long vendorId, BigDecimal amount, LocalDate date, Boolean splitPayment) {}

    public record Reason(String reason, String severity) {}

    public record RiskSignalDto(
            String name,
            String severity,
            String evidence,
            int contribution,
            String explanation
    ) {}

    public record EvidenceNode(String id, String label, String detail, String severity) {}

    public record SafeAlternative(
            String id,
            String title,
            String description,
            String riskLevel,
            boolean safe
    ) {}

    public record EvaluateResponse(
            Long caseId,
            String decision,
            int riskScore,
            String riskBand,
            List<RiskSignalDto> riskSignals,
            List<Reason> reasons,
            List<String> missingEvidence,
            String recommendation,
            List<EvidenceNode> evidence,
            List<SafeAlternative> safeAlternatives,
            VendorProfileResponse vendorProfile,
            Map<String, Object> context
    ) {}

    public record VendorProfileResponse(
            Long vendorId,
            String name,
            String category,
            String email,
            long vendorAgeDays,
            int paymentCount,
            BigDecimal averagePayment,
            BigDecimal totalPaid,
            String activeAccount,
            long accountAgeHours,
            long openInvoices,
            boolean duplicateInvoiceRisk,
            int baselineRiskScore
    ) {}

    public record SimulateRequest(Long vendorId, BigDecimal amount, LocalDate date) {}

    public record Scenario(
            String id,
            String name,
            String description,
            BigDecimal minimumCash,
            String risk,
            String note
    ) {}

    public record SimulateResponse(List<Scenario> scenarios, String strongestLiquidity) {}

    public record ChatRequest(Long caseId, String message) {}

    public record ChatResponse(String answer, boolean llmUsed) {}

    public record ReviewRequest(String note, String actor) {}

    public record AuditEntryDto(Long id, String action, String actor, String note, Instant timestamp) {}

    public record DashboardStats(
            BigDecimal cashPosition,
            BigDecimal todaysPayments,
            long highRisk,
            long awaitingReview,
            String company
    ) {}

    public record PolicyResponse(
            BigDecimal maxPaymentWithoutApproval,
            int bankCoolingHours,
            BigDecimal newVendorMaxPayment,
            BigDecimal minCashBuffer,
            boolean invoiceRequired
    ) {}

    public record RazorpayPayoutRequest(String accountNumber, String ifsc) {}

    public record RazorpayPayoutResponse(
            String payoutId,
            String status,
            String message,
            boolean simulated
    ) {}

    public record EvaluationMetrics(
            int totalCases,
            int trainSize,
            int valSize,
            int testSize,
            double precision,
            double recall,
            double f1,
            double falsePositiveRate,
            double falseNegativeRate,
            Map<String, Map<String, Integer>> confusionMatrix,
            String datasetNote
    ) {}
}
