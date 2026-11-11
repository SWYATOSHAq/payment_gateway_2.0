package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.repository.CheckoutRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CloudPaymentsNotificationsTest {
    private static final UUID INVOICE = UUID.fromString("ad432702-708a-41de-990e-e4c8f23b1433");
    private static final byte[] BODY = ("InvoiceId=" + INVOICE
            + "&Amount=125.50&Currency=RUB&Status=Completed&OperationType=Payment"
            + "&TestMode=1&TransactionId=51&PaymentMethod=Card").getBytes(StandardCharsets.UTF_8);
    private final CheckoutRepository payments = mock(CheckoutRepository.class);
    private final CloudPaymentsNotifications notifications = new CloudPaymentsNotifications(payments, "test-secret", true);

    @Test
    void accepts_signed_test_payment() throws Exception {
        when(payments.complete(INVOICE, new BigDecimal("125.50"), 51, "Card")).thenReturn(true);
        notifications.pay(BODY, signature(BODY));
        verify(payments).complete(INVOICE, new BigDecimal("125.50"), 51, "Card");
    }

    @Test
    void rejects_modified_body_before_updating_database() throws Exception {
        byte[] modified = new String(BODY, StandardCharsets.UTF_8)
                .replace("125.50", "100.00").getBytes(StandardCharsets.UTF_8);
        var error = assertThrows(ResponseStatusException.class, () -> notifications.pay(modified, signature(BODY)));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(payments);
    }

    @Test
    void rejects_non_test_payment() throws Exception {
        byte[] live = new String(BODY, StandardCharsets.UTF_8)
                .replace("TestMode=1", "TestMode=0").getBytes(StandardCharsets.UTF_8);
        var error = assertThrows(ResponseStatusException.class, () -> notifications.pay(live, signature(live)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(payments);
    }

    @Test
    void signed_check_delegates_amount_and_transaction() throws Exception {
        when(payments.check(INVOICE, new BigDecimal("125.50"), 51, true)).thenReturn(12);
        assertEquals(12, notifications.check(BODY, signature(BODY)));
    }

    @Test
    void check_refuses_a_live_payment() throws Exception {
        byte[] live = new String(BODY, StandardCharsets.UTF_8)
                .replace("TestMode=1", "TestMode=0").getBytes(StandardCharsets.UTF_8);
        assertEquals(13, notifications.check(live, signature(live)));
        verifyNoInteractions(payments);
    }

    @Test
    void signed_fail_releases_the_checked_attempt() throws Exception {
        byte[] failed = new String(BODY, StandardCharsets.UTF_8)
                .replace("&Status=Completed", "").getBytes(StandardCharsets.UTF_8);
        notifications.fail(failed, signature(failed));
        verify(payments).release(INVOICE, new BigDecimal("125.50"), 51);
    }

    private static String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
