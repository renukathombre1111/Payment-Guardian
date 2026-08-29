package com.novatech.paymentguardian.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashPosition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private BigDecimal openingBalance;
    private BigDecimal inflow;
    private BigDecimal outflow;
    private BigDecimal closingBalance;
}
