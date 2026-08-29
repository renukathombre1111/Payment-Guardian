package com.novatech.paymentguardian.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long vendorId;
    private BigDecimal amount;
    private LocalDate proposedDate;
    private String decision;
    private int riskScore;
    private double confidence;
    private String status;
    private String recommendation;
    @Column(length = 4000)
    private String evidenceJson;
    @Column(length = 4000)
    private String reasonsJson;
    @Column(length = 8000)
    private String riskSignalsJson;
    private String riskBand;
    private Instant createdAt;
    private Instant reviewedAt;
}
