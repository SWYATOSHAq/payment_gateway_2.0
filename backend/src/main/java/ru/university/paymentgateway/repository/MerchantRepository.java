package ru.university.paymentgateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.university.paymentgateway.domain.Merchant;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {}
