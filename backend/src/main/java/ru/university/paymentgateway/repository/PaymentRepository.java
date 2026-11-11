package ru.university.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.university.paymentgateway.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByUuid(UUID uuid);

    @Query("select p from Payment p where p.merchant.id = :merchantId and p.order_number = :orderNumber")
    Optional<Payment> find_order(long merchantId, String orderNumber);

    @Override
    @EntityGraph(attributePaths = "merchant")
    Page<Payment> findAll(Specification<Payment> specification, Pageable pageable);
}
