package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudPaymentsClientTest {
    @Test
    void creates_one_stage_payment_link() {
        var builder = RestClient.builder().baseUrl("https://api.cloudpayments.ru");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new CloudPaymentsClient(builder.build(), "terminal", "test-secret");
        var id = UUID.fromString("ad432702-708a-41de-990e-e4c8f23b1433");
        server.expect(requestTo("https://api.cloudpayments.ru/orders/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Request-ID", id.toString()))
                .andExpect(content().json("""
                        {"Amount":125.50,"Currency":"RUB","Description":"Заказ 7",
                        "InvoiceId":"ad432702-708a-41de-990e-e4c8f23b1433",
                        "RequireConfirmation":false,"SendEmail":false}
                        """))
                .andRespond(withSuccess("""
                        {"Success":true,"Model":{"Url":"https://orders.cloudpayments.ru/d/test"}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("https://orders.cloudpayments.ru/d/test",
                client.create_link(id, new BigDecimal("125.50"), "Заказ 7"));
        server.verify();
    }
}
