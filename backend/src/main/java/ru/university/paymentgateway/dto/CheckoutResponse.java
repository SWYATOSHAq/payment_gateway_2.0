package ru.university.paymentgateway.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ru.university.paymentgateway.domain.CheckoutPayment;

public record CheckoutResponse(UUID id, BigDecimal amount, String currency, String status,
                               String paymentUrl, String paymentMethod) {
    public static CheckoutResponse from(CheckoutPayment payment) {
        return new CheckoutResponse(payment.uuid(), payment.amount(), "RUB", payment.status(),
                payment.payment_url(), payment.payment_method());
    }
}
