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
    private final CloudPaymentsNotifications notifications = new CloudPaymentsNotifications(payments, "test-secret");

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

    private static String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
