package com.carrypick.repo;

import com.carrypick.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTossOrderId(String tossOrderId);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
