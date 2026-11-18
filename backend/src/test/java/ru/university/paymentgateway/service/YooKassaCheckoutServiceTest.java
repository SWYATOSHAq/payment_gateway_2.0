package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.university.paymentgateway.domain.CheckoutPayment;
import ru.university.paymentgateway.repository.CheckoutRepository;
import ru.university.paymentgateway.repository.CheckoutRepository.YooKassaPayment;
import ru.university.paymentgateway.service.YooKassaClient.RemotePayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YooKassaCheckoutServiceTest {
    private static final UUID ID = UUID.randomUUID();
    private static final String EXTERNAL_ID = "22d6d597-000f-5000-9000-145f6df21d6f";
    private static final String TOKEN = "ct-test";
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");

    private final CheckoutService checkout = mock(CheckoutService.class);
    private final CheckoutRepository payments = mock(CheckoutRepository.class);
    private final YooKassaClient client = mock(YooKassaClient.class);
    private final YooKassaCheckoutService service = new YooKassaCheckoutService(checkout, payments, client);

    @BeforeEach
    void enable_test_store() {
        when(client.configured()).thenReturn(true);
    }

    @Test
    void pending_status_uses_stored_token_when_provider_omits_confirmation() {
        when(payments.yookassa(ID)).thenReturn(Optional.of(payment("CREATED", TOKEN)));
        when(client.get(EXTERNAL_ID, ID, AMOUNT)).thenReturn(remote("pending"));

        var view = service.find(ID);

        assertThat(view.canPay()).isTrue();
        assertThat(view.confirmationToken()).isEqualTo(TOKEN);
        verify(payments, never()).finish_yookassa(ID, EXTERNAL_ID, true);
    }

    @Test
    void confirms_payment_and_stops_polling_provider_after_completion() {
        when(payments.yookassa(ID)).thenReturn(Optional.of(payment("CREATED", TOKEN)),
                Optional.of(payment("COMPLETED", null)));
        when(client.get(EXTERNAL_ID, ID, AMOUNT)).thenReturn(remote("succeeded"));

        var completed = service.find(ID);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.paymentMethod()).isEqualTo("bank_card");
        assertThat(completed.confirmationToken()).isNull();
        assertThat(completed.canPay()).isFalse();
        assertThat(service.find(ID)).isEqualTo(completed);
        verify(payments).finish_yookassa(ID, EXTERNAL_ID, true);
        verify(client).get(EXTERNAL_ID, ID, AMOUNT);
    }

    @Test
    void persists_cancellation_without_releasing_token() {
        when(payments.yookassa(ID)).thenReturn(Optional.of(payment("CREATED", TOKEN)),
                Optional.of(payment("CANCELLED", null)));
        when(client.get(EXTERNAL_ID, ID, AMOUNT)).thenReturn(remote("canceled"));

        var cancelled = service.find(ID);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.confirmationToken()).isNull();
        assertThat(cancelled.canPay()).isFalse();
        verify(payments).finish_yookassa(ID, EXTERNAL_ID, false);
    }

    @Test
    void repeated_creation_uses_existing_provider_payment() {
        when(checkout.reserve("Bearer key", "7", AMOUNT, true, "YOOKASSA", true))
                .thenReturn(new CheckoutPayment(ID, AMOUNT, "CREATED", Instant.now(), null, null, true, "YOOKASSA"));
        when(payments.yookassa(ID)).thenReturn(Optional.of(payment("CREATED", TOKEN)));
        when(client.get(EXTERNAL_ID, ID, AMOUNT)).thenReturn(remote("pending"));

        assertThat(service.create("Bearer key", "7", AMOUNT).id()).isEqualTo(ID);
        verify(client, never()).create(ID, AMOUNT, "Заказ 7");
    }

    private static YooKassaPayment payment(String status, String token) {
        return new YooKassaPayment(ID, "7", "Магазин", "Заказ 7", AMOUNT, status,
                "COMPLETED".equals(status) ? "bank_card" : null, EXTERNAL_ID, token, true);
    }

    private static RemotePayment remote(String status) {
        return new RemotePayment(EXTERNAL_ID, status, true, "succeeded".equals(status), null, null, null, null);
    }
}
