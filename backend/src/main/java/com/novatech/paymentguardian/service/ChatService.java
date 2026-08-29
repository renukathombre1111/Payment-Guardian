package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.dto.ApiDtos.ChatResponse;
import com.novatech.paymentguardian.dto.ApiDtos.EvaluateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    @Value("${guardian.openai.api-key:}")
    private String apiKey;

    public ChatResponse answer(String message, EvaluateResponse context) {
        String q = message == null ? "" : message.toLowerCase();
        String text;
        if (q.contains("tomorrow") || q.contains("pay tomorrow")) {
            text = "The expected ₹35L customer receivable arrives tomorrow. Paying tomorrow or after receivable would increase the projected minimum cash balance compared to paying today. Scenario C (pay after receivable) typically preserves the strongest liquidity position. The new bank account should still be verified — timing alone does not remove that risk.";
        } else if (q.contains("split")) {
            text = "Paying ₹10L today and the remainder after the receivable reduces liquidity risk, but the new bank account should still be verified. Split payment is a MEDIUM liquidity path, not a reason to skip account confirmation.";
        } else if (q.contains("stop") || q.contains("why") || q.contains("block") || q.contains("review")) {
            text = "Invoice "
                    + String.valueOf(context.context().get("invoice"))
                    + " matches the vendor and amount. I flagged this because risk signals include: "
                    + context.riskSignals().stream()
                    .filter(s -> s.contribution() > 0)
                    .map(s -> s.name() + " (" + s.severity() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("policy checks")
                    + ". Decision: " + context.decision() + " (score " + context.riskScore() + "/100, band "
                    + context.riskBand() + ") — AI recommendation only; human approval required.";
        } else {
            text = "Payment Guardian recommends " + context.decision()
                    + " for ₹" + context.context().get("amount") + " to " + context.context().get("vendor")
                    + ". Risk score " + context.riskScore() + "/100 (" + context.riskBand() + "). "
                    + context.recommendation()
                    + " Ask why it was flagged, what if you pay tomorrow, or what if you split the payment.";
        }
        return new ChatResponse(text, llmConfigured());
    }

    public boolean llmConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
