package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.domain.CashPosition;
import com.novatech.paymentguardian.domain.Obligation;
import com.novatech.paymentguardian.dto.ApiDtos.Scenario;
import com.novatech.paymentguardian.dto.ApiDtos.SimulateRequest;
import com.novatech.paymentguardian.dto.ApiDtos.SimulateResponse;
import com.novatech.paymentguardian.repo.CashPositionRepository;
import com.novatech.paymentguardian.repo.ObligationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class SimulationService {

    private final CashPositionRepository cashRepo;
    private final ObligationRepository obligationRepo;

    public SimulationService(CashPositionRepository cashRepo, ObligationRepository obligationRepo) {
        this.cashRepo = cashRepo;
        this.obligationRepo = obligationRepo;
    }

    public SimulateResponse simulate(SimulateRequest request) {
        CashPosition cash = cashRepo.findTopByOrderByDateDesc().orElseThrow();
        List<Obligation> obligations = obligationRepo.findAllByOrderByDueDateAsc().stream()
                .filter(o -> !"VENDOR".equalsIgnoreCase(o.getType()))
                .toList();
        LocalDate today = request.date() == null ? LocalDate.of(2026, 8, 27) : request.date();
        BigDecimal amount = request.amount();
        BigDecimal start = cash.getClosingBalance();

        BigDecimal receivable = obligations.stream()
                .filter(o -> "RECEIVABLE".equalsIgnoreCase(o.getType()))
                .map(Obligation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate receivableDate = obligations.stream()
                .filter(o -> "RECEIVABLE".equalsIgnoreCase(o.getType()))
                .map(Obligation::getDueDate)
                .min(Comparator.naturalOrder())
                .orElse(today.plusDays(1));

        Scenario a = scenario("A", "Pay today", minCash(start, amount, today, today, obligations),
                "Full amount leaves immediately.");
        Scenario b = scenario("B", "Pay after receivable", minCash(start, amount, receivableDate, today, obligations),
                "Expected ₹35L inflow arrives before the debit.");
        Scenario c = scenario("C", "Pay in 3 days", minCash(start, amount, today.plusDays(3), today, obligations),
                "Payroll and GST still land in the window.");
        BigDecimal first = new BigDecimal("1000000");
        BigDecimal afterFirst = minCash(start, first, today, today, obligations);
        Scenario d = scenario("D", "Split payment",
                afterFirst.min(minCash(start.subtract(first).add(receivable), amount.subtract(first), receivableDate, today, List.of())),
                "₹10L today, remainder after receivable. Still verify the new account.");

        List<Scenario> scenarios = List.of(a, b, c, d);
        String strongest = scenarios.stream().max(Comparator.comparing(Scenario::minimumCash)).map(Scenario::name).orElse("");
        return new SimulateResponse(scenarios, strongest + " preserves the strongest liquidity position.");
    }

    private Scenario scenario(String id, String name, BigDecimal min, String note) {
        String risk = min.compareTo(new BigDecimal("4500000")) >= 0 ? "LOW"
                : min.compareTo(new BigDecimal("3500000")) >= 0 ? "MEDIUM" : "HIGH";
        return new Scenario(id, name, name, min, risk, note);
    }

    private BigDecimal minCash(BigDecimal starting, BigDecimal payment, LocalDate payDate, LocalDate today, List<Obligation> obligations) {
        BigDecimal cash = starting;
        BigDecimal min = cash;
        for (int i = 0; i <= 12; i++) {
            LocalDate day = today.plusDays(i);
            for (Obligation o : obligations) {
                if ("RECEIVABLE".equalsIgnoreCase(o.getType()) && day.equals(o.getDueDate())) {
                    cash = cash.add(o.getAmount());
                } else if (!"RECEIVABLE".equalsIgnoreCase(o.getType()) && day.equals(o.getDueDate())) {
                    cash = cash.subtract(o.getAmount());
                }
            }
            if (day.equals(payDate)) {
                cash = cash.subtract(payment);
            }
            min = min.min(cash);
        }
        return min;
    }
}
