package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YooKassaClientTest {
    private static final String PAYMENTS = "https://api.yookassa.ru/v3/payments";
    private static final UUID ID = UUID.fromString("ad432702-708a-41de-990e-e4c8f23b1433");
    private static final String EXTERNAL_ID = "22d6d597-000f-5000-9000-145f6df21d6f";
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");

    private MockRestServiceServer server;
    private RestClient rest;
    private YooKassaClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://api.yookassa.ru/v3");
        server = MockRestServiceServer.bindTo(builder).build();
        rest = builder.build();
        client = new YooKassaClient(rest, "123456", "test_secret");
    }

    @Test
    void creates_embedded_payment_with_stable_idempotency_key() {
        server.expect(requestTo(PAYMENTS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic MTIzNDU2OnRlc3Rfc2VjcmV0"))
                .andExpect(header("Idempotence-Key", ID.toString()))
                .andExpect(content().json("""
                        {"amount":{"value":"125.50","currency":"RUB"},"capture":true,
                         "confirmation":{"type":"embedded"},"description":"Заказ 7",
                         "metadata":{"checkout_id":"ad432702-708a-41de-990e-e4c8f23b1433"}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"pending","test":true,"paid":false,
                         "amount":{"value":"125.50","currency":"RUB"},"metadata":{"checkout_id":"%s"},
                         "confirmation":{"type":"embedded","confirmation_token":"ct-test"}}
                        """.formatted(EXTERNAL_ID, ID), MediaType.APPLICATION_JSON));

        assertThat(client.create(ID, AMOUNT, "Заказ 7").confirmation().token()).isEqualTo("ct-test");
        server.verify();
    }

    @Test
    void verifies_matching_test_card_payment() {
        server.expect(requestTo(PAYMENTS + "/" + EXTERNAL_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(completed_response(true, "bank_card", AMOUNT), MediaType.APPLICATION_JSON));

        assertThat(client.get(EXTERNAL_ID, ID, AMOUNT).status()).isEqualTo("succeeded");
        server.verify();
    }

    @Test
    void rejects_non_test_response() {
        server.expect(requestTo(PAYMENTS + "/" + EXTERNAL_ID))
                .andRespond(withSuccess(completed_response(false, "bank_card", AMOUNT), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.get(EXTERNAL_ID, ID, AMOUNT)).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejects_wrong_amount_or_non_card_payment() {
        server.expect(requestTo(PAYMENTS + "/" + EXTERNAL_ID))
                .andRespond(withSuccess(completed_response(true, "bank_card", BigDecimal.ONE), MediaType.APPLICATION_JSON));
        server.expect(requestTo(PAYMENTS + "/" + EXTERNAL_ID))
                .andRespond(withSuccess(completed_response(true, "sbp", AMOUNT), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.get(EXTERNAL_ID, ID, AMOUNT)).isInstanceOf(RestClientException.class);
        assertThatThrownBy(() -> client.get(EXTERNAL_ID, ID, AMOUNT)).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejects_live_key_before_network_request() {
        var live = new YooKassaClient(rest, "123456", "live_secret");

        assertThatThrownBy(() -> live.create(ID, AMOUNT, "Заказ 7")).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejects_payment_for_another_local_order() {
        String response = completed_response(true, "bank_card", AMOUNT)
                .replace(ID.toString(), UUID.randomUUID().toString());
        server.expect(requestTo(PAYMENTS + "/" + EXTERNAL_ID))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.get(EXTERNAL_ID, ID, AMOUNT)).isInstanceOf(RestClientException.class);
        server.verify();
    }

    private static String completed_response(boolean test, String method, BigDecimal amount) {
        return """
                {"id":"%s","status":"succeeded","test":%s,"paid":true,
                 "amount":{"value":"%s","currency":"RUB"},"metadata":{"checkout_id":"%s"},
                 "payment_method":{"type":"%s"}}
                """.formatted(EXTERNAL_ID, test, amount.toPlainString(), ID, method);
    }
}
