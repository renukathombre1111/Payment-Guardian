package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.domain.*;
import com.novatech.paymentguardian.dto.ApiDtos.SafeAlternative;
import com.novatech.paymentguardian.dto.ApiDtos.Scenario;
import com.novatech.paymentguardian.dto.ApiDtos.SimulateResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SafeAlternativesService {

    private final SimulationService simulation;

    public SafeAlternativesService(SimulationService simulation) {
        this.simulation = simulation;
    }

    public List<SafeAlternative> suggest(
            String decision,
            int riskScore,
            SimulateResponse simulation,
            PolicyEngine.PolicyResult policyResult
    ) {
        if ("APPROVE".equals(decision)) {
            return List.of(new SafeAlternative(
                    "proceed",
                    "Proceed with payment",
                    "Risk score " + riskScore + " is within approval range",
                    "LOW",
                    true));
        }

        List<SafeAlternative> alternatives = new ArrayList<>();
        Scenario best = simulation.scenarios().stream()
                .max(java.util.Comparator.comparing(Scenario::minimumCash))
                .orElse(null);

        if (best != null && !"Pay today".equals(best.name())) {
            alternatives.add(new SafeAlternative(
                    "defer-" + best.id(),
                    best.name(),
                    best.description() + " — " + best.note(),
                    best.risk(),
                    true));
        }

        alternatives.add(new SafeAlternative(
                "verify-bank",
                "Verify bank account change",
                "Contact vendor via known channel to confirm new account before any payment",
                "LOW",
                true));

        if (policyResult.violations().stream().anyMatch(v -> "INVOICE_REQUIRED".equals(v.code()))) {
            alternatives.add(new SafeAlternative(
                    "obtain-invoice",
                    "Obtain matching invoice",
                    "Upload a valid open invoice matching the payment amount",
                    "LOW",
                    true));
        } else {
            alternatives.add(new SafeAlternative(
                    "split-safe",
                    "Split payment after receivable",
                    "Pay partial amount after confirmed inflow to preserve cash buffer",
                    "MEDIUM",
                !policyResult.violations().stream().anyMatch(v -> "BANK_COOLING".equals(v.code()))));
        }

        if ("BLOCK".equals(decision)) {
            alternatives.add(new SafeAlternative(
                    "escalate",
                    "Escalate to finance leadership",
                    "Critical risk — requires senior approval before reconsidering",
                    "HIGH",
                    true));
        }

        return alternatives.stream()
                .filter(SafeAlternative::safe)
                .toList();
    }
}
