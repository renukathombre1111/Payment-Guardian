package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.Obligation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObligationRepository extends JpaRepository<Obligation, Long> {
    List<Obligation> findAllByOrderByDueDateAsc();
}
