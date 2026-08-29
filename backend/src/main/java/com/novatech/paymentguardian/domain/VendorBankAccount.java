package com.novatech.paymentguardian.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VendorBankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long vendorId;
    private String accountNumber;
    private String ifsc;
    private Instant createdAt;
    private boolean active;
}
