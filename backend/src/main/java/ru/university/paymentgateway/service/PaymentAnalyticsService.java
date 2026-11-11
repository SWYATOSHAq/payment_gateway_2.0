package ru.university.paymentgateway.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.university.paymentgateway.dto.PaymentAnalyticsSummary;
import ru.university.paymentgateway.repository.PaymentAnalyticsRepository;

@Service
public class PaymentAnalyticsService {
    private final PaymentAnalyticsRepository analytics;

    public PaymentAnalyticsService(PaymentAnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentAnalyticsSummary summary() {
        var totals = analytics.totals();
        return new PaymentAnalyticsSummary(totals.total(), totals.active(), totals.archived(),
                totals.needs_review(), totals.average_amount(), analytics.by_merchant(), analytics.by_status(),
                analytics.by_amount_category());
    }
}
