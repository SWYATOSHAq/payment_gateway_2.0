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
import ru.university.paymentgateway.domain.CheckoutPayment;
import ru.university.paymentgateway.dto.CheckoutResponse;
import ru.university.paymentgateway.dto.EmbeddedCheckoutResponse;
import ru.university.paymentgateway.repository.CheckoutRepository;

@Service
public class CheckoutService {
    private final CheckoutRepository payments;
    private final CloudPaymentsClient cloudpayments;
    private final long merchant_id;
    private final String checkout_key;
    private final boolean provider_configured;
    private final String public_id;
    private final boolean embedded_enabled;

    public CheckoutService(CheckoutRepository payments, CloudPaymentsClient cloudpayments,
                           @Value("${gateway.checkout.merchant-id}") long merchant_id,
                           @Value("${gateway.checkout.key}") String checkout_key,
                           @Value("${gateway.cloudpayments.public-id}") String public_id,
                           @Value("${gateway.cloudpayments.api-secret}") String api_secret,
                           @Value("${gateway.cloudpayments.embedded-enabled}") boolean embedded_enabled) {
        this.payments = payments;
        this.cloudpayments = cloudpayments;
        this.merchant_id = merchant_id;
        this.checkout_key = checkout_key;
        this.public_id = public_id;
        this.embedded_enabled = embedded_enabled;
        this.provider_configured = !public_id.isBlank() && !api_secret.isBlank();
    }

    public CheckoutResponse create(String authorization, String order_number, BigDecimal amount) {
        CheckoutPayment payment = reserve(authorization, order_number, amount, false);
        if (payment.payment_url() != null || !"CREATED".equals(payment.status())) {
            return CheckoutResponse.from(payment);
        }
        // CloudPayments stores idempotent results for one hour.
        if (payment.checkout_created_at().plus(Duration.ofMinutes(55)).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось подтвердить создание счёта. Проверьте его в CloudPayments");
        }

        String url;
        try {
            url = cloudpayments.create_link(payment.uuid(), payment.amount(), "Заказ " + order_number.strip());
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CloudPayments не вернул ссылку на оплату");
        }
        payments.save_link(payment.uuid(), url);
        return find(payment.uuid());
    }

    public EmbeddedCheckoutResponse create_embedded(String authorization, String order_number, BigDecimal amount) {
        if (!embedded_enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Встроенная оплата не настроена");
        }
        return find_embedded(reserve(authorization, order_number, amount, true).uuid());
    }

    public EmbeddedCheckoutResponse find_embedded(UUID id) {
        var payment = payments.embedded(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
        boolean can_pay = embedded_enabled && provider_configured && payment.available() && !payment.processing()
                && "CREATED".equals(payment.status());
        return new EmbeddedCheckoutResponse(payment.id(), payment.order_number(), payment.merchant_name(),
                payment.description(), payment.amount(), "RUB", payment.status(), payment.payment_method(),
                can_pay ? public_id : null, can_pay, payment.processing());
    }

    private CheckoutPayment reserve(String authorization, String order_number, BigDecimal amount, boolean embedded) {
        if (checkout_key.isBlank() || !provider_configured || merchant_id <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Оплата не настроена");
        }
        if (authorization == null || !MessageDigest.isEqual(
                ("Bearer " + checkout_key).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный ключ магазина");
        }

        String order = order_number == null ? "" : order_number.strip();
        if (order.isEmpty() || order.length() > 100 || order.indexOf('\0') >= 0 || amount == null || amount.signum() <= 0
                || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте номер заказа и сумму");
        }

        var payment = payments.reserve(merchant_id, order, amount.setScale(2, RoundingMode.UNNECESSARY), embedded)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Магазин не настроен или отключён"));
        if (payment.checkout_created_at() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер заказа уже используется ручным платежом");
        }
        if (payment.embedded() != embedded) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Для заказа уже выбран другой сценарий оплаты");
        }
        if (payment.amount().compareTo(amount) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ уже создан с другой суммой");
        }
        return payment;
    }

    public CheckoutResponse find(UUID id) {
        return payments.find(id).map(CheckoutResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
    }
}
