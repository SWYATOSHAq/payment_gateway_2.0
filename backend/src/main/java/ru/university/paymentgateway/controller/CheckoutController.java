package ru.university.paymentgateway.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.university.paymentgateway.config.OpenApiConfiguration;
import ru.university.paymentgateway.dto.CheckoutResponse;
import ru.university.paymentgateway.service.CheckoutService;
import ru.university.paymentgateway.service.CloudPaymentsNotifications;

@Tag(name = "Checkout и CloudPayments")
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
    @Operation(operationId = "createCheckoutPayment", summary = "Создать платёжную ссылку")
    @SecurityRequirement(name = OpenApiConfiguration.CHECKOUT_KEY)
    public CheckoutResponse create(@Parameter(hidden = true)
                                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestBody CreateCheckoutPayment request) {
        return checkout.create(authorization, request.orderNumber(), request.amount());
    }

    @GetMapping("/payments/{id}")
    @Operation(operationId = "getCheckoutPayment", summary = "Проверить статус checkout-платежа")
    public CheckoutResponse find(@PathVariable UUID id) {
        return checkout.find(id);
    }

    @PostMapping(value = "/cloudpayments/pay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(operationId = "receiveCloudPaymentsPay", summary = "Принять уведомление CloudPayments Pay",
            description = "CloudPayments подписывает исходное тело формы. Принимаются тестовые одностадийные платежи.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                            schema = @Schema(implementation = CloudPaymentsPayForm.class))))
    public Map<String, Integer> pay(@Parameter(hidden = true) @RequestBody byte[] body,
                                    @Parameter(required = true, description = "Подпись исходного тела в Base64")
                                    @RequestHeader(value = "Content-HMAC", required = false) String signature) {
        notifications.pay(body, signature);
        return Map.of("code", 0);
    }

    public record CloudPaymentsPayForm(@JsonProperty("InvoiceId") UUID invoiceId,
                                       @JsonProperty("Amount") BigDecimal amount,
                                       @JsonProperty("Currency") @Schema(allowableValues = "RUB") String currency,
                                       @JsonProperty("Status") @Schema(allowableValues = "Completed") String status,
                                       @JsonProperty("OperationType") @Schema(allowableValues = "Payment") String operationType,
                                       @JsonProperty("TestMode") @Schema(allowableValues = "1") String testMode,
                                       @JsonProperty("TransactionId") long transactionId,
                                       @JsonProperty("PaymentMethod") String paymentMethod) {}

    public record CreateCheckoutPayment(String orderNumber, BigDecimal amount) {}
}
