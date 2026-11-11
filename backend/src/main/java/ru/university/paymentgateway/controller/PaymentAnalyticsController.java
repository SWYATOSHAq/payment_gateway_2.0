package ru.university.paymentgateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.config.OpenApiConfiguration;
import ru.university.paymentgateway.dto.PaymentAnalyticsSummary;
import ru.university.paymentgateway.service.PaymentAnalyticsService;

@Tag(name = "Аналитика")
@SecurityRequirement(name = OpenApiConfiguration.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/analytics")
public class PaymentAnalyticsController {
    private final PaymentAnalyticsService analytics;

    public PaymentAnalyticsController(PaymentAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    @Operation(operationId = "getPaymentAnalytics", summary = "Получить сводку по платежам")
    public PaymentAnalyticsSummary summary() {
        return analytics.summary();
    }
}
