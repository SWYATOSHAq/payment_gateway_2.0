package ru.university.paymentgateway.controller;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.CheckoutResponse;
import ru.university.paymentgateway.service.CheckoutService;
import ru.university.paymentgateway.service.CloudPaymentsNotifications;

@RestController
@RequestMapping("/api")
public class CheckoutController {
    private final CheckoutService checkout;
    private final CloudPaymentsNotifications notifications;

    public CheckoutController(CheckoutService checkout, CloudPaymentsNotifications notifications) {
        this.checkout = checkout;
        this.notifications = notifications;
    }

    @PostMapping("/payments")
    public CheckoutResponse create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestBody CreatePayment request) {
        return checkout.create(authorization, request.orderNumber(), request.amount());
    }

    @GetMapping("/payments/{id}")
    public CheckoutResponse find(@PathVariable UUID id) {
        return checkout.find(id);
    }

    @PostMapping(value = "/cloudpayments/pay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Integer> pay(@RequestBody byte[] body,
                                    @RequestHeader(value = "Content-HMAC", required = false) String signature) {
        notifications.pay(body, signature);
        return Map.of("code", 0);
    }

    public record CreatePayment(String orderNumber, BigDecimal amount) {}
}
