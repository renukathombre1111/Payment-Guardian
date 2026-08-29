package com.novatech.paymentguardian.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.*;
import com.novatech.paymentguardian.dto.ApiDtos.*;
import com.novatech.paymentguardian.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvestigationService {

    private final VendorRepository vendors;
    private final VendorTransactionRepository transactions;
    private final VendorBankAccountRepository accounts;
    private final InvoiceRepository invoices;
    private final CashPositionRepository cashRepo;
    private final ObligationRepository obligations;
    private final PaymentCaseRepository cases;
    private final PolicyEngine policyEngine;
    private final RiskEngine riskEngine;
    private final VendorProfileService vendorProfile;
    private final SimulationService simulation;
    private final SafeAlternativesService safeAlternatives;
    private final ObjectMapper mapper;

    public InvestigationService(
            VendorRepository vendors,
            VendorTransactionRepository transactions,
            VendorBankAccountRepository accounts,
            InvoiceRepository invoices,
            CashPositionRepository cashRepo,
            ObligationRepository obligations,
            PaymentCaseRepository cases,
            PolicyEngine policyEngine,
            RiskEngine riskEngine,
            VendorProfileService vendorProfile,
            SimulationService simulation,
            SafeAlternativesService safeAlternatives,
            ObjectMapper mapper
    ) {
        this.vendors = vendors;
        this.transactions = transactions;
        this.accounts = accounts;
        this.invoices = invoices;
        this.cashRepo = cashRepo;
        this.obligations = obligations;
        this.cases = cases;
        this.policyEngine = policyEngine;
        this.riskEngine = riskEngine;
        this.vendorProfile = vendorProfile;
        this.simulation = simulation;
        this.safeAlternatives = safeAlternatives;
        this.mapper = mapper;
    }

    @Transactional
    public EvaluateResponse evaluate(EvaluateRequest request) {
        Instant evalTime = Instant.parse("2026-08-27T16:00:00Z");
        ComputedEvaluation computed = compute(request);
        String recommendation = buildRecommendation(
                computed.decision(), computed.risk(), computed.policy(), computed.alternatives());

        PaymentCase saved = cases.save(PaymentCase.builder()
                .vendorId(request.vendorId())
                .amount(request.amount())
                .proposedDate(request.date() == null ? LocalDate.of(2026, 8, 27) : request.date())
                .decision(computed.decision())
                .riskScore(computed.risk().totalScore())
                .riskBand(computed.risk().riskBand())
                .confidence(0)
                .status("AWAITING_REVIEW")
                .recommendation(recommendation)
                .evidenceJson(write(computed.evidence()))
                .reasonsJson(write(computed.reasons()))
                .riskSignalsJson(write(computed.signalDtos()))
                .createdAt(evalTime)
                .build());

        return new EvaluateResponse(
                saved.getId(), computed.decision(), computed.risk().totalScore(), computed.risk().riskBand(),
                computed.signalDtos(), computed.reasons(), computed.missing(), recommendation,
                computed.evidence(), computed.alternatives(), computed.profile(), computed.context());
    }

    public EvaluateResponse getCase(Long id) {
        PaymentCase pc = cases.findById(id).orElseThrow();
        Vendor vendor = vendors.findById(pc.getVendorId()).orElseThrow();
        List<Reason> reasons = readList(pc.getReasonsJson(), Reason.class);
        List<EvidenceNode> evidence = readList(pc.getEvidenceJson(), EvidenceNode.class);
        List<RiskSignalDto> signals = readList(pc.getRiskSignalsJson(), RiskSignalDto.class);

        EvaluateRequest req = new EvaluateRequest(pc.getVendorId(), pc.getAmount(), pc.getProposedDate(), false);
        ComputedEvaluation computed = compute(req);

        return new EvaluateResponse(
                pc.getId(), pc.getDecision(), pc.getRiskScore(),
                pc.getRiskBand() != null ? pc.getRiskBand() : computed.risk().riskBand(),
                signals.isEmpty() ? computed.signalDtos() : signals,
                reasons.isEmpty() ? computed.reasons() : reasons,
                computed.missing(),
                pc.getRecommendation(),
                evidence.isEmpty() ? computed.evidence() : evidence,
                computed.alternatives(),
                computed.profile(),
                computed.context());
    }

    private ComputedEvaluation compute(EvaluateRequest request) {
        Instant evalTime = Instant.parse("2026-08-27T16:00:00Z");
        Vendor vendor = vendors.findById(request.vendorId()).orElseThrow();
        List<VendorTransaction> history = transactions.findByVendorIdOrderByTimestampDesc(vendor.getId());
        List<VendorBankAccount> bankAccounts = accounts.findByVendorIdOrderByCreatedAtDesc(vendor.getId());
        List<Invoice> vendorInvoices = invoices.findByVendorId(vendor.getId());
        CashPosition cash = cashRepo.findTopByOrderByDateDesc().orElseThrow();
        List<Obligation> upcoming = obligations.findAllByOrderByDueDateAsc();

        BigDecimal upcomingOut = upcoming.stream()
                .filter(o -> !"RECEIVABLE".equalsIgnoreCase(o.getType()))
                .map(Obligation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PolicyEngine.PolicyResult policy = policyEngine.evaluate(
                vendor, request.amount(), bankAccounts, vendorInvoices,
                cash.getClosingBalance(), upcomingOut, evalTime);

        Invoice matching = vendorInvoices.stream()
                .filter(i -> i.getAmount().compareTo(request.amount()) == 0
                        && "OPEN".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElse(null);

        boolean split = Boolean.TRUE.equals(request.splitPayment());
        RiskEngine.RiskResult risk = riskEngine.score(new RiskEngine.RiskContext(
                request.amount(),
                request.date() == null ? LocalDate.of(2026, 8, 27) : request.date(),
                vendor, history, bankAccounts, vendorInvoices, matching,
                cash.getClosingBalance(), upcomingOut, policy, split, evalTime));

        String decision = RiskEngine.actionFor(risk.totalScore(), policy);
        List<Reason> reasons = buildReasons(risk, policy);
        List<String> missing = buildMissing(policy, risk);
        List<EvidenceNode> evidence = buildEvidenceChain(
                request, vendor, matching, bankAccounts, history, cash, upcoming, policy, risk);
        VendorProfileResponse profile = vendorProfile.build(vendor, history, bankAccounts, vendorInvoices, evalTime);
        SimulateResponse sim = simulation.simulate(new SimulateRequest(
                vendor.getId(), request.amount(),
                request.date() == null ? LocalDate.of(2026, 8, 27) : request.date()));
        List<SafeAlternative> alternatives = safeAlternatives.suggest(decision, risk.totalScore(), sim, policy);
        Map<String, Object> context = buildContext(vendor, request, risk, cash, upcoming, matching, policy);
        List<RiskSignalDto> signalDtos = risk.signals().stream()
                .map(s -> new RiskSignalDto(s.name(), s.severity(), s.evidence(), s.contribution(), s.explanation()))
                .toList();

        return new ComputedEvaluation(risk, policy, decision, reasons, missing, evidence, alternatives, profile, context, signalDtos);
    }

    private record ComputedEvaluation(
            RiskEngine.RiskResult risk,
            PolicyEngine.PolicyResult policy,
            String decision,
            List<Reason> reasons,
            List<String> missing,
            List<EvidenceNode> evidence,
            List<SafeAlternative> alternatives,
            VendorProfileResponse profile,
            Map<String, Object> context,
            List<RiskSignalDto> signalDtos
    ) {}

    @Transactional
    public PaymentCase review(Long id, String status, String note) {
        PaymentCase pc = cases.findById(id).orElseThrow();
        pc.setStatus(status);
        pc.setReviewedAt(Instant.now());
        if (note != null && !note.isBlank()) {
            pc.setRecommendation(pc.getRecommendation() + " | Human: " + note);
        }
        return cases.save(pc);
    }

    private List<Reason> buildReasons(RiskEngine.RiskResult risk, PolicyEngine.PolicyResult policy) {
        List<Reason> reasons = new ArrayList<>();
        risk.signals().stream()
                .filter(s -> s.contribution() > 0)
                .sorted((a, b) -> Integer.compare(b.contribution(), a.contribution()))
                .forEach(s -> reasons.add(new Reason(s.explanation() + " — " + s.evidence(), s.severity())));
        policy.violations().forEach(v -> reasons.add(new Reason(v.message(), v.severity())));
        return reasons;
    }

    private List<String> buildMissing(PolicyEngine.PolicyResult policy, RiskEngine.RiskResult risk) {
        List<String> missing = new ArrayList<>();
        if (!policy.invoicePresent()) {
            missing.add("Valid open invoice matching payment amount");
        }
        risk.signals().stream()
                .filter(s -> "bank-account-change".equals(s.name()) && s.contribution() >= 12)
                .findFirst()
                .ifPresent(s -> missing.add("Vendor confirmation of bank-account change"));
        return missing;
    }

    private List<EvidenceNode> buildEvidenceChain(
            EvaluateRequest request,
            Vendor vendor,
            Invoice matching,
            List<VendorBankAccount> bankAccounts,
            List<VendorTransaction> history,
            CashPosition cash,
            List<Obligation> upcoming,
            PolicyEngine.PolicyResult policy,
            RiskEngine.RiskResult risk
    ) {
        List<EvidenceNode> evidence = new ArrayList<>();
        evidence.add(new EvidenceNode("payment", "Payment",
                "₹" + request.amount() + " proposed", "INFO"));
        evidence.add(new EvidenceNode("vendor", "Vendor", vendor.getName() + " (" + vendor.getCategory() + ")", "INFO"));

        if (matching != null) {
            evidence.add(new EvidenceNode("invoice", "Invoice",
                    matching.getInvoiceNumber() + " · ₹" + matching.getAmount() + " · " + matching.getStatus(),
                    "LOW"));
        } else {
            evidence.add(new EvidenceNode("invoice", "Invoice", "No matching open invoice", "CRITICAL"));
        }

        VendorBankAccount active = bankAccounts.stream().filter(VendorBankAccount::isActive).findFirst().orElse(null);
        if (active != null) {
            String sev = risk.signals().stream()
                    .filter(s -> "bank-account-change".equals(s.name()))
                    .map(RiskEngine.RiskSignal::severity)
                    .findFirst().orElse("LOW");
            evidence.add(new EvidenceNode("bank", "Bank Account",
                    active.getAccountNumber() + " · IFSC " + active.getIfsc(), sev));
        }

        long paidCount = history.stream()
                .filter(t -> "PAID".equalsIgnoreCase(t.getStatus())).count();
        evidence.add(new EvidenceNode("history", "Historical Transactions",
                paidCount + " settled payments · avg ₹" + risk.historicalAverage(), "INFO"));

        evidence.add(new EvidenceNode("cash", "Cash Position",
                "Closing ₹" + cash.getClosingBalance(), "INFO"));

        BigDecimal receivable = upcoming.stream()
                .filter(o -> "RECEIVABLE".equalsIgnoreCase(o.getType()))
                .map(Obligation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        evidence.add(new EvidenceNode("receivable", "Expected Inflows",
                "₹" + receivable + " receivable", receivable.signum() > 0 ? "LOW" : "MEDIUM"));

        if (!policy.violations().isEmpty()) {
            evidence.add(new EvidenceNode("policy", "Policy Violations",
                    policy.violations().size() + " rule(s) triggered", "HIGH"));
        }

        risk.signals().stream()
                .filter(s -> s.contribution() > 0)
                .forEach(s -> evidence.add(new EvidenceNode("signal-" + s.name(), "Risk: " + s.name(),
                        s.evidence() + " (+" + s.contribution() + ")", s.severity())));

        evidence.add(new EvidenceNode("decision", "Final Decision",
                risk.riskBand() + " · score " + risk.totalScore() + "/100 → " + risk.recommendedAction(),
                risk.totalScore() >= 81 ? "CRITICAL" : risk.totalScore() >= 31 ? "HIGH" : "LOW"));

        return evidence;
    }

    private String buildRecommendation(
            String decision,
            RiskEngine.RiskResult risk,
            PolicyEngine.PolicyResult policy,
            List<SafeAlternative> alternatives
    ) {
        if ("BLOCK".equals(decision)) {
            return "Do not execute payment. Critical risk score " + risk.totalScore()
                    + "/100 or policy violation. Consider: "
                    + alternatives.stream().map(SafeAlternative::title).limit(2).reduce((a, b) -> a + "; " + b).orElse("escalate");
        }
        if ("REVIEW".equals(decision)) {
            return "Human review required. Score " + risk.totalScore() + "/100 ("
                    + risk.riskBand() + "). Verify bank account and timing before approval.";
        }
        return "Payment consistent with vendor history, liquidity, and policy. Human approval still required before execution.";
    }

    private Map<String, Object> buildContext(
            Vendor vendor,
            EvaluateRequest request,
            RiskEngine.RiskResult risk,
            CashPosition cash,
            List<Obligation> upcoming,
            Invoice matching,
            PolicyEngine.PolicyResult policy
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("vendor", vendor.getName());
        context.put("vendorId", vendor.getId());
        context.put("amount", request.amount());
        context.put("averagePayment", risk.historicalAverage());
        context.put("multiplier", risk.multiplier());
        context.put("currentCash", cash.getClosingBalance());
        context.put("payroll", upcoming.stream().filter(o -> "PAYROLL".equals(o.getType()))
                .map(Obligation::getAmount).findFirst().orElse(BigDecimal.ZERO));
        context.put("gst", upcoming.stream().filter(o -> "TAX".equals(o.getType()))
                .map(Obligation::getAmount).findFirst().orElse(BigDecimal.ZERO));
        context.put("expectedReceivable", upcoming.stream().filter(o -> "RECEIVABLE".equals(o.getType()))
                .map(Obligation::getAmount).findFirst().orElse(BigDecimal.ZERO));
        context.put("invoice", matching == null ? null : matching.getInvoiceNumber());
        context.put("riskBand", risk.riskBand());
        context.put("policyViolations", policy.violations().size());
        context.put("humanApprovalRequired", true);
        context.put("syntheticData", true);
        return context;
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return mapper.readValue(json, mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private <T> List<T> readList(String json, Class<T> type) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            return List.of();
        }
    }
}
