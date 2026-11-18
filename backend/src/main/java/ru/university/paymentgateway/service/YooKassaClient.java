package ru.university.paymentgateway.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class YooKassaClient {
    private final RestClient client;
    private final String shop_id;
    private final String secret_key;

    @Autowired
    public YooKassaClient(RestClient.Builder builder,
                         @Value("${gateway.yookassa.shop-id}") String shop_id,
                         @Value("${gateway.yookassa.secret-key}") String secret_key) {
        this(builder.requestFactory(request_factory()).baseUrl("https://api.yookassa.ru/v3").build(),
                shop_id, secret_key);
    }

    YooKassaClient(RestClient client, String shop_id, String secret_key) {
        this.client = client;
        this.shop_id = shop_id;
        this.secret_key = secret_key;
    }

    boolean configured() {
        return shop_id.matches("[0-9]+") && secret_key.startsWith("test_") && secret_key.length() > 5;
    }

    RemotePayment create(UUID id, BigDecimal amount, String description) {
        require_test_credentials();
        RemotePayment payment = client.post().uri("/payments")
                .headers(headers -> headers.setBasicAuth(shop_id, secret_key))
                .header("Idempotence-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", Map.of("value", amount.toPlainString(), "currency", "RUB"), "capture", true,
                        "confirmation", Map.of("type", "embedded"), "description", description,
                        "metadata", Map.of("checkout_id", id.toString())))
                .retrieve().body(RemotePayment.class);
        return validate(payment, id, amount);
    }

    RemotePayment get(String external_id, UUID id, BigDecimal amount) {
        require_test_credentials();
        RemotePayment payment = client.get().uri("/payments/{id}", external_id)
                .headers(headers -> headers.setBasicAuth(shop_id, secret_key))
                .retrieve().body(RemotePayment.class);
        payment = validate(payment, id, amount);
        if (!external_id.equals(payment.id())) {
            throw new RestClientException("Unexpected YooKassa payment ID");
        }
        return payment;
    }

    private void require_test_credentials() {
        if (!configured()) {
            throw new RestClientException("YooKassa test credentials are required");
        }
    }

    private static RemotePayment validate(RemotePayment payment, UUID id, BigDecimal amount) {
        if (payment == null || payment.id() == null || payment.id().isBlank() || payment.id().length() > 64
                || !payment.test() || payment.status() == null || payment.amount() == null || !"RUB".equals(payment.amount().currency())
                || payment.amount().value() == null || amount.compareTo(payment.amount().value()) != 0
                || payment.metadata() == null || !id.toString().equals(payment.metadata().get("checkout_id"))) {
            throw new RestClientException("Unexpected YooKassa test payment");
        }
        if ("succeeded".equals(payment.status()) && (!payment.paid()
                || payment.method() == null || !"bank_card".equals(payment.method().type()))) {
            throw new RestClientException("Unexpected YooKassa payment method");
        }
        return payment;
    }

    private static JdkClientHttpRequestFactory request_factory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    record Amount(BigDecimal value, String currency) {}

    record Confirmation(String type, @JsonProperty("confirmation_token") String token) {}
    record PaymentMethod(String type) {}

    record RemotePayment(String id, String status, boolean test, boolean paid, Amount amount,
                         Map<String, String> metadata, Confirmation confirmation,
                         @JsonProperty("payment_method") PaymentMethod method) {}
}
