package com.novatech.paymentguardian.web;

import com.novatech.paymentguardian.config.PolicyProperties;
import com.novatech.paymentguardian.domain.*;
import com.novatech.paymentguardian.dto.ApiDtos.*;
import com.novatech.paymentguardian.repo.*;
import com.novatech.paymentguardian.service.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final VendorRepository vendors;
    private final VendorTransactionRepository transactions;
    private final InvoiceRepository invoices;
    private final CashPositionRepository cashRepo;
    private final ObligationRepository obligations;
    private final PaymentCaseRepository cases;
    private final BankTransactionRepository bankTx;
    private final VendorBankAccountRepository accounts;
    private final InvestigationService investigation;
    private final SimulationService simulation;
    private final ChatService chat;
    private final AuditService audit;
    private final PolicyProperties policyProperties;
    private final VendorProfileService vendorProfile;
    private final RazorpayAdapter razorpay;
    private final EvaluationService evaluation;

    public ApiController(
            VendorRepository vendors,
            VendorTransactionRepository transactions,
            InvoiceRepository invoices,
            CashPositionRepository cashRepo,
            ObligationRepository obligations,
            PaymentCaseRepository cases,
            BankTransactionRepository bankTx,
            VendorBankAccountRepository accounts,
            InvestigationService investigation,
            SimulationService simulation,
            ChatService chat,
            AuditService audit,
            PolicyProperties policyProperties,
            VendorProfileService vendorProfile,
            RazorpayAdapter razorpay,
            EvaluationService evaluation
    ) {
        this.vendors = vendors;
        this.transactions = transactions;
        this.invoices = invoices;
        this.cashRepo = cashRepo;
        this.obligations = obligations;
        this.cases = cases;
        this.bankTx = bankTx;
        this.accounts = accounts;
        this.investigation = investigation;
        this.simulation = simulation;
        this.chat = chat;
        this.audit = audit;
        this.policyProperties = policyProperties;
        this.vendorProfile = vendorProfile;
        this.razorpay = razorpay;
        this.evaluation = evaluation;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "payment-guardian",
                "humanApprovalRequired", true,
                "razorpaySimulated", razorpay.isSimulated(),
                "syntheticData", true
        );
    }

    @GetMapping("/policy")
    public PolicyResponse policy() {
        return new PolicyResponse(
                policyProperties.getMaxPaymentWithoutApproval(),
                policyProperties.getBankCoolingHours(),
                policyProperties.getNewVendorMaxPayment(),
                policyProperties.getMinCashBuffer(),
                policyProperties.isInvoiceRequired()
        );
    }

    @GetMapping("/vendors")
    public List<Vendor> vendors() {
        return vendors.findAll();
    }

    @GetMapping("/vendors/{id}/profile")
    public VendorProfileResponse vendorProfile(@PathVariable Long id) {
        Vendor vendor = vendors.findById(id).orElseThrow();
        return vendorProfile.build(
                vendor,
                transactions.findByVendorIdOrderByTimestampDesc(id),
                accounts.findByVendorIdOrderByCreatedAtDesc(id),
                invoices.findByVendorId(id),
                java.time.Instant.parse("2026-08-27T16:00:00Z")
        );
    }

    @GetMapping("/transactions")
    public List<VendorTransaction> transactions(@RequestParam(required = false) Long vendorId) {
        if (vendorId == null) return transactions.findAll();
        return transactions.findByVendorIdOrderByTimestampDesc(vendorId);
    }

    @GetMapping("/invoices")
    public List<Invoice> invoices() {
        return invoices.findAll();
    }

    @GetMapping("/bank-transactions")
    public List<BankTransaction> bankTransactions() {
        return bankTx.findAll();
    }

    @GetMapping("/cash")
    public List<CashPosition> cash() {
        return cashRepo.findAll();
    }

    @GetMapping("/obligations")
    public List<Obligation> obligations() {
        return obligations.findAllByOrderByDueDateAsc();
    }

    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        BigDecimal cash = cashRepo.findTopByOrderByDateDesc().map(CashPosition::getClosingBalance).orElse(BigDecimal.ZERO);
        BigDecimal todayPay = obligations.findAllByOrderByDueDateAsc().stream()
                .filter(o -> LocalDate.of(2026, 8, 27).equals(o.getDueDate()) && !"RECEIVABLE".equals(o.getType()))
                .map(Obligation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardStats(
                cash,
                todayPay,
                cases.countByDecision("REVIEW") + cases.countByDecision("BLOCK"),
                cases.countByStatus("AWAITING_REVIEW"),
                "NovaTech Pvt Ltd"
        );
    }

    @GetMapping("/cases")
    public List<PaymentCase> cases() {
        return cases.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/payments/evaluate")
    public EvaluateResponse evaluate(@RequestBody EvaluateRequest request) {
        return investigation.evaluate(request);
    }

    @GetMapping("/cases/{id}")
    public EvaluateResponse getCase(@PathVariable Long id) {
        return investigation.getCase(id);
    }

    @GetMapping("/cases/{id}/evidence")
    public EvaluateResponse evidence(@PathVariable Long id) {
        return investigation.getCase(id);
    }

    @GetMapping("/cases/{id}/audit")
    public List<AuditEntryDto> auditTrail(@PathVariable Long id) {
        return audit.history(id);
    }

    @PostMapping("/simulate-payment")
    public SimulateResponse simulate(@RequestBody SimulateRequest request) {
        return simulation.simulate(request);
    }

    @PostMapping("/cases/{id}/chat")
    public ChatResponse chat(@PathVariable Long id, @RequestBody ChatRequest request) {
        EvaluateResponse ctx = investigation.getCase(id);
        return chat.answer(request.message(), ctx);
    }

    @PostMapping("/cases/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        PaymentCase pc = audit.recordAction(id, "APPROVE",
                body == null ? null : body.actor(),
                body == null ? null : body.note());
        return ResponseEntity.ok(Map.of(
                "id", pc.getId(),
                "status", pc.getStatus(),
                "message", "Human approved. Execution is not automatic — no money was moved."
        ));
    }

    @PostMapping("/cases/{id}/hold")
    public ResponseEntity<?> hold(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        PaymentCase pc = audit.recordAction(id, "HOLD",
                body == null ? null : body.actor(),
                body == null ? null : body.note());
        return ResponseEntity.ok(Map.of(
                "id", pc.getId(),
                "status", pc.getStatus(),
                "message", "Payment held. AI recommendation — human approval required."
        ));
    }

    @PostMapping("/cases/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        PaymentCase pc = audit.recordAction(id, "REJECT",
                body == null ? null : body.actor(),
                body == null ? null : body.note());
        return ResponseEntity.ok(Map.of(
                "id", pc.getId(),
                "status", pc.getStatus(),
                "message", "Payment rejected. No transfer will be initiated."
        ));
    }

    @PostMapping("/cases/{id}/escalate")
    public ResponseEntity<?> escalate(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        PaymentCase pc = audit.recordAction(id, "ESCALATE",
                body == null ? null : body.actor(),
                body == null ? null : body.note());
        return ResponseEntity.ok(Map.of(
                "id", pc.getId(),
                "status", pc.getStatus(),
                "message", "Escalated to finance leadership for review."
        ));
    }

    @PostMapping("/cases/{id}/razorpay/payout")
    public RazorpayPayoutResponse razorpayPayout(
            @PathVariable Long id,
            @RequestBody(required = false) RazorpayPayoutRequest body
    ) {
        PaymentCase pc = cases.findById(id).orElseThrow();
        if (!"APPROVED_PENDING_EXECUTION".equals(pc.getStatus())) {
            return new RazorpayPayoutResponse(null, "DENIED",
                    "Simulated payout denied — case must be human-approved first.", true);
        }
        VendorBankAccount acct = accounts.findByVendorIdOrderByCreatedAtDesc(pc.getVendorId()).stream()
                .filter(VendorBankAccount::isActive).findFirst().orElseThrow();
        RazorpayAdapter.PayoutRecord record = razorpay.initiatePayout(new RazorpayAdapter.PayoutRequest(
                pc.getId(), pc.getVendorId(), pc.getAmount(),
                body == null ? acct.getAccountNumber() : body.accountNumber(),
                body == null ? acct.getIfsc() : body.ifsc()));
        return new RazorpayPayoutResponse(record.payoutId(), record.status(), record.message(), record.simulated());
    }

    @GetMapping("/evaluation/metrics")
    public EvaluationMetrics evaluationMetrics() {
        return evaluation.loadMetrics();
    }
}
