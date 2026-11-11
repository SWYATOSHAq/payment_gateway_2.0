package ru.university.paymentgateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutPayment(UUID uuid, BigDecimal amount, String status,
                              Instant checkout_created_at, String payment_url, String payment_method) {}
