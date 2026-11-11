package ru.university.paymentgateway.domain;

import java.math.BigDecimal;

public enum AmountCategory {
    SMALL, MEDIUM, LARGE;

    public static final BigDecimal MEDIUM_FROM = BigDecimal.valueOf(10_000);
    public static final BigDecimal LARGE_FROM = BigDecimal.valueOf(100_000);

    public static AmountCategory from(BigDecimal amount) {
        if (amount.compareTo(MEDIUM_FROM) < 0) {
            return SMALL;
        }
        return amount.compareTo(LARGE_FROM) < 0 ? MEDIUM : LARGE;
    }
}
