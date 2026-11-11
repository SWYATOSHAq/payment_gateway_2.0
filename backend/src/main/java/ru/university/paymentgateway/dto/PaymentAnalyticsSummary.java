package ru.university.paymentgateway.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import ru.university.paymentgateway.domain.AmountCategory;
import ru.university.paymentgateway.domain.Payment.Status;

public record PaymentAnalyticsSummary(long total, long active, long archived, long needsReview,
                                      BigDecimal averageAmount, List<MerchantCount> byMerchant,
                                      Map<Status, Long> byStatus, Map<AmountCategory, Long> byAmountCategory) {
    public record MerchantCount(long merchantId, String name, long count) {}
}
