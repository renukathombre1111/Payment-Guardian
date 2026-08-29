package com.novatech.paymentguardian.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long caseId;
    private String action;
    private String actor;
    @Column(length = 2000)
    private String note;
    private Instant timestamp;
}
