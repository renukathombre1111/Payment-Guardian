package com.novatech.paymentguardian.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated Razorpay payout adapter — no real money movement or production integration.
 */
@Service
public class RazorpayAdapter {

    private final Map<String, PayoutRecord> payouts = new ConcurrentHashMap<>();

    public record PayoutRequest(Long caseId, Long vendorId, BigDecimal amount, String accountNumber, String ifsc) {}

    public record PayoutRecord(
            String payoutId,
            Long caseId,
            String status,
            String message,
            boolean simulated
    ) {}

    public PayoutRecord initiatePayout(PayoutRequest request) {
        String id = "sim_pout_" + UUID.randomUUID().toString().substring(0, 12);
        PayoutRecord record = new PayoutRecord(
                id,
                request.caseId(),
                "SIMULATED_QUEUED",
                "Simulated Razorpay payout queued for ₹" + request.amount()
                        + ". No real transfer executed. Human approval required before any production adapter.",
                true);
        payouts.put(id, record);
        return record;
    }

    public PayoutRecord status(String payoutId) {
        return payouts.getOrDefault(payoutId,
                new PayoutRecord(payoutId, null, "NOT_FOUND", "Simulated payout not found", true));
    }

    public boolean isSimulated() {
        return true;
    }
}
