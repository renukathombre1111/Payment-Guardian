package com.novatech.paymentguardian.repo;

import com.novatech.paymentguardian.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Optional<Vendor> findByNameIgnoreCase(String name);
}
