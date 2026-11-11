package ru.university.paymentgateway.domain;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountCategoryTest {
    @ParameterizedTest
    @CsvSource({"9999.99, SMALL", "10000.00, MEDIUM", "99999.99, MEDIUM", "100000.00, LARGE"})
    void classifies_amount_at_category_boundaries(String amount, AmountCategory category) {
        assertEquals(category, AmountCategory.from(new BigDecimal(amount)));
    }
}
