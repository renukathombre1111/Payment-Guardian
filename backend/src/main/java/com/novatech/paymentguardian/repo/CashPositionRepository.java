package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.CashPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface CashPositionRepository extends JpaRepository<CashPosition, Long> {
    Optional<CashPosition> findByDate(LocalDate date);
    Optional<CashPosition> findTopByOrderByDateDesc();
}
