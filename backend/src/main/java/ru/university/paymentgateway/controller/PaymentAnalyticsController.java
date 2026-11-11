package ru.university.paymentgateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.PaymentAnalyticsSummary;
import ru.university.paymentgateway.service.PaymentAnalyticsService;

@RestController
@RequestMapping("/api/analytics")
public class PaymentAnalyticsController {
    private final PaymentAnalyticsService analytics;

    public PaymentAnalyticsController(PaymentAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public PaymentAnalyticsSummary summary() {
        return analytics.summary();
    }
}
