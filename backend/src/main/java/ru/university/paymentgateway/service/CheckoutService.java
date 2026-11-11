package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.dto.CheckoutResponse;
import ru.university.paymentgateway.repository.CheckoutRepository;

@Service
public class CheckoutService {
    private final CheckoutRepository payments;
    private final CloudPaymentsClient cloudpayments;
    private final long merchant_id;
    private final String checkout_key;
    private final boolean provider_configured;

    public CheckoutService(CheckoutRepository payments, CloudPaymentsClient cloudpayments,
                           @Value("${gateway.checkout.merchant-id}") long merchant_id,
                           @Value("${gateway.checkout.key}") String checkout_key,
                           @Value("${gateway.cloudpayments.public-id}") String public_id,
                           @Value("${gateway.cloudpayments.api-secret}") String api_secret) {
        this.payments = payments;
        this.cloudpayments = cloudpayments;
        this.merchant_id = merchant_id;
        this.checkout_key = checkout_key;
        this.provider_configured = !public_id.isBlank() && !api_secret.isBlank();
    }

    public CheckoutResponse create(String authorization, String order_number, BigDecimal amount) {
        if (checkout_key.isBlank() || !provider_configured || merchant_id <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Оплата не настроена");
        }
        if (authorization == null || !MessageDigest.isEqual(
                ("Bearer " + checkout_key).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный ключ магазина");
        }

        String order = order_number == null ? "" : order_number.strip();
        if (order.isEmpty() || order.length() > 100 || amount == null || amount.signum() <= 0
                || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте номер заказа и сумму");
        }

        var payment = payments.reserve(merchant_id, order, amount.setScale(2, RoundingMode.UNNECESSARY))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Магазин не настроен или отключён"));
        if (payment.checkout_created_at() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер заказа уже используется ручным платежом");
        }
        if (payment.amount().compareTo(amount) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ уже создан с другой суммой");
        }
        if (payment.payment_url() != null || !"CREATED".equals(payment.status())) {
            return CheckoutResponse.from(payment);
        }
        // CloudPayments stores idempotent results for one hour.
        if (payment.checkout_created_at().plus(Duration.ofMinutes(55)).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось подтвердить создание счёта. Проверьте его в CloudPayments");
        }

        String url;
        try {
            url = cloudpayments.create_link(payment.uuid(), payment.amount(), "Заказ " + order);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CloudPayments не вернул ссылку на оплату");
        }
        payments.save_link(payment.uuid(), url);
        return find(payment.uuid());
    }

    public CheckoutResponse find(UUID id) {
        return payments.find(id).map(CheckoutResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
    }
}
