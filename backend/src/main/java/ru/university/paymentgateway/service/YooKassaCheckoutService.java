package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.dto.YooKassaCheckoutResponse;
import ru.university.paymentgateway.repository.CheckoutRepository;
import ru.university.paymentgateway.repository.CheckoutRepository.YooKassaPayment;
import ru.university.paymentgateway.service.YooKassaClient.RemotePayment;

@Service
public class YooKassaCheckoutService {
    private final CheckoutService checkout;
    private final CheckoutRepository payments;
    private final YooKassaClient client;

    public YooKassaCheckoutService(CheckoutService checkout, CheckoutRepository payments, YooKassaClient client) {
        this.checkout = checkout;
        this.payments = payments;
        this.client = client;
    }

    public YooKassaCheckoutResponse create(String authorization, String order_number, BigDecimal amount) {
        var reserved = checkout.reserve(authorization, order_number, amount, true, "YOOKASSA", client.configured());
        var payment = find_payment(reserved.uuid());
        if (payment.external_id() != null || !payment.available() || !"CREATED".equals(payment.status())) {
            return refresh(payment);
        }
        // YooKassa deduplicates creation requests only within 24 hours.
        if (reserved.checkout_created_at().plus(Duration.ofHours(23)).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не удалось подтвердить создание платежа. Проверьте его в ЮKassa");
        }
        try {
            var remote = client.create(payment.id(), payment.amount(), "Заказ " + payment.order_number());
            String token = remote.confirmation() != null && "embedded".equals(remote.confirmation().type())
                    ? remote.confirmation().token() : null;
            if ("pending".equals(remote.status()) && (token == null || token.isBlank())) {
                throw new RestClientException("YooKassa did not return a confirmation token");
            }
            payments.save_yookassa(payment.id(), remote.id(), token);
            return apply_remote(find_payment(payment.id()), remote);
        } catch (RestClientException exception) {
            throw provider_error(exception);
        }
    }

    public YooKassaCheckoutResponse find(UUID id) {
        return refresh(find_payment(id));
    }

    private YooKassaCheckoutResponse refresh(YooKassaPayment payment) {
        if (payment.external_id() == null || !"CREATED".equals(payment.status()) || !client.configured()) {
            return response(payment, false, false);
        }
        try {
            return apply_remote(payment, client.get(payment.external_id(), payment.id(), payment.amount()));
        } catch (RestClientException exception) {
            throw provider_error(exception);
        }
    }

    private YooKassaCheckoutResponse apply_remote(YooKassaPayment payment, RemotePayment remote) {
        if (!remote.id().equals(payment.external_id())) {
            throw new RestClientException("YooKassa payment does not match the checkout");
        }
        return switch (remote.status()) {
            case "succeeded", "canceled" -> {
                payments.finish_yookassa(payment.id(), remote.id(), "succeeded".equals(remote.status()));
                yield response(find_payment(payment.id()), false, false);
            }
            case "pending" -> {
                var current = find_payment(payment.id());
                boolean can_pay = current.available() && "CREATED".equals(current.status())
                        && current.token() != null;
                yield response(current, can_pay, false);
            }
            case "waiting_for_capture" -> response(find_payment(payment.id()), false, true);
            default -> throw new RestClientException("Unknown YooKassa payment status");
        };
    }

    private YooKassaPayment find_payment(UUID id) {
        return payments.yookassa(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
    }

    private static YooKassaCheckoutResponse response(YooKassaPayment payment, boolean can_pay, boolean processing) {
        return new YooKassaCheckoutResponse(payment.id(), payment.order_number(), payment.merchant_name(),
                payment.description(), payment.amount(), "RUB", payment.status(), payment.payment_method(),
                can_pay ? payment.token() : null, can_pay, processing && "CREATED".equals(payment.status()));
    }

    private static ResponseStatusException provider_error(RestClientException exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Не удалось получить данные тестового платежа ЮKassa", exception);
    }
}
