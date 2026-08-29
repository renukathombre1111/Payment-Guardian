package com.novatech.paymentguardian.service;

import com.novatech.paymentguardian.domain.AuditEntry;
import com.novatech.paymentguardian.domain.PaymentCase;
import com.novatech.paymentguardian.dto.ApiDtos.AuditEntryDto;
import com.novatech.paymentguardian.repo.AuditEntryRepository;
import com.novatech.paymentguardian.repo.PaymentCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {

    private final AuditEntryRepository auditRepo;
    private final PaymentCaseRepository cases;

    public AuditService(AuditEntryRepository auditRepo, PaymentCaseRepository cases) {
        this.auditRepo = auditRepo;
        this.cases = cases;
    }

    @Transactional
    public PaymentCase recordAction(Long caseId, String action, String actor, String note) {
        PaymentCase pc = cases.findById(caseId).orElseThrow();
        String status = mapStatus(action);
        pc.setStatus(status);
        pc.setReviewedAt(Instant.now());
        if (note != null && !note.isBlank()) {
            pc.setRecommendation(pc.getRecommendation() + " | " + action + ": " + note);
        }
        cases.save(pc);
        auditRepo.save(AuditEntry.builder()
                .caseId(caseId)
                .action(action)
                .actor(actor == null ? "human-reviewer" : actor)
                .note(note)
                .timestamp(Instant.now())
                .build());
        return pc;
    }

    public List<AuditEntryDto> history(Long caseId) {
        return auditRepo.findByCaseIdOrderByTimestampDesc(caseId).stream()
                .map(e -> new AuditEntryDto(e.getId(), e.getAction(), e.getActor(), e.getNote(), e.getTimestamp()))
                .toList();
    }

    private String mapStatus(String action) {
        return switch (action.toUpperCase()) {
            case "APPROVE" -> "APPROVED_PENDING_EXECUTION";
            case "HOLD" -> "HELD";
            case "REJECT" -> "REJECTED";
            case "ESCALATE" -> "ESCALATED";
            default -> action.toUpperCase();
        };
    }
}
