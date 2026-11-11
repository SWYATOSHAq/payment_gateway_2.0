package ru.university.paymentgateway.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyPath;
import ru.university.paymentgateway.controller.PaymentCabinetController.OrderBy;
import ru.university.paymentgateway.domain.Payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PaymentSortTest {
    @ParameterizedTest
    @EnumSource(OrderBy.class)
    void sort_uses_resolvable_payment_fields(OrderBy order) {
        for (Sort.Order field : order.to_sort(Sort.Direction.DESC)) {
            assertDoesNotThrow(() -> PropertyPath.from(field.getProperty(), Payment.class));
        }
    }
}
