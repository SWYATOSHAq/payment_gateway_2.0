package ru.university.paymentgateway.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CloudPaymentsClient {
    private final RestClient client;
    private final String public_id;
    private final String api_secret;

    @Autowired
    public CloudPaymentsClient(RestClient.Builder builder,
                               @Value("${gateway.cloudpayments.public-id}") String public_id,
                               @Value("${gateway.cloudpayments.api-secret}") String api_secret) {
        this(builder.requestFactory(request_factory()).baseUrl("https://api.cloudpayments.ru").build(),
                public_id, api_secret);
    }

    CloudPaymentsClient(RestClient client, String public_id, String api_secret) {
        this.client = client;
        this.public_id = public_id;
        this.api_secret = api_secret;
    }

    private static SimpleClientHttpRequestFactory request_factory() {
        var request_factory = new SimpleClientHttpRequestFactory();
        request_factory.setConnectTimeout(Duration.ofSeconds(5));
        request_factory.setReadTimeout(Duration.ofSeconds(10));
        return request_factory;
    }

    String create_link(UUID id, BigDecimal amount, String description) {
        var result = client.post().uri("/orders/create")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBasicAuth(public_id, api_secret))
                .header("X-Request-ID", id.toString())
                .body(Map.of("InvoiceId", id.toString(), "Amount", amount, "Currency", "RUB",
                        "Description", description, "RequireConfirmation", false, "SendEmail", false))
                .retrieve().body(OrderResponse.class);

        if (result == null || !result.success() || result.model() == null || result.model().url() == null) {
            throw new RestClientException("CloudPayments did not return a payment link");
        }
        URI url = URI.create(result.model().url());
        if (!"https".equals(url.getScheme()) || !"orders.cloudpayments.ru".equals(url.getHost())
                || url.getUserInfo() != null || url.getPort() != -1) {
            throw new RestClientException("Unexpected CloudPayments payment link");
        }
        return url.toString();
    }

    private record OrderResponse(@JsonProperty("Success") boolean success, @JsonProperty("Model") Order model) {}
    private record Order(@JsonProperty("Url") String url) {}
}
