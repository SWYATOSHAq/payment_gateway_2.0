package ru.university.paymentgateway.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.university.paymentgateway.domain.AmountCategory;
import ru.university.paymentgateway.domain.Payment;

public record PaymentView(UUID id, long number, String orderNumber, BigDecimal amount, String currency,
                          String description, MerchantView merchant, Payment.Status status, boolean needsReview,
                          List<String> tags, AmountCategory amountCategory, Instant createdAt, Instant updatedAt,
                          long version, boolean archived, boolean cloudPayments, String paymentMethod) {
    public static PaymentView from(Payment payment) {
        return new PaymentView(payment.uuid(), payment.id(), payment.order_number(), payment.amount(),
                payment.currency(), payment.description(), MerchantView.from(payment.merchant()), payment.status(),
                payment.needs_review(), payment.tags().stream().sorted().toList(), AmountCategory.from(payment.amount()),
                payment.created_at(), payment.updated_at(), payment.version(), payment.archived(),
                payment.cloudpayments(), payment.payment_method());
    }
}
