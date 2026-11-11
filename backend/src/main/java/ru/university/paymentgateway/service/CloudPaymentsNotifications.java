package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.repository.CheckoutRepository;

@Service
public class CloudPaymentsNotifications {
    private final CheckoutRepository payments;
    private final String api_secret;

    public CloudPaymentsNotifications(CheckoutRepository payments,
                                      @Value("${gateway.cloudpayments.api-secret}") String api_secret) {
        this.payments = payments;
        this.api_secret = api_secret;
    }

    public void pay(byte[] body, String signature) {
        if (!valid_signature(body, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверная подпись CloudPayments");
        }

        boolean completed;
        try {
            Map<String, String> fields = parse_form(body);
            String method = fields.get("PaymentMethod");
            if (method == null && fields.containsKey("CardType")) {
                method = "Card";
            }
            UUID invoice = UUID.fromString(fields.getOrDefault("InvoiceId", ""));
            BigDecimal amount = new BigDecimal(fields.getOrDefault("Amount", ""));
            long transaction = Long.parseLong(fields.getOrDefault("TransactionId", ""));
            if (!"RUB".equals(fields.get("Currency")) || !"Completed".equals(fields.get("Status"))
                    || !"Payment".equals(fields.get("OperationType")) || !"1".equals(fields.get("TestMode"))
                    || transaction <= 0 || amount.signum() <= 0 || (method != null && method.length() > 64)) {
                throw new IllegalArgumentException("Invalid payment notification");
            }
            completed = payments.complete(invoice, amount, transaction, method);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректное уведомление CloudPayments");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Транзакция уже связана с другим платежом");
        }
        if (!completed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Уведомление не соответствует платежу");
        }
    }

    private boolean valid_signature(byte[] body, String signature) {
        if (api_secret.isBlank() || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(api_secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(body), Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static Map<String, String> parse_form(byte[] body) {
        Map<String, String> fields = new HashMap<>();
        for (String entry : new String(body, StandardCharsets.UTF_8).split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || fields.putIfAbsent(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null) {
                throw new IllegalArgumentException("Invalid form data");
            }
        }
        return fields;
    }
}
