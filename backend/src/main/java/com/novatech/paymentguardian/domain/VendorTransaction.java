package com.novatech.paymentguardian.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vendor_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VendorTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long vendorId;
    private BigDecimal amount;
    private String type;
    private String status;
    private Instant timestamp;
    private String reference;
}
