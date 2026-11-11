package ru.university.paymentgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Merchant;
import ru.university.paymentgateway.domain.Payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentCsvCodecTest {
    private static final String HEADER = "merchantId,orderNumber,amount,currency,description,status,needsReview,tags\n";
    private final PaymentCsvCodec codec = new PaymentCsvCodec(new ObjectMapper());

    @Test
    void round_trips_utf8_quotes_and_formula_like_text() throws Exception {
        var merchant = new Merchant("Shop", "https://example.test");
        ReflectionTestUtils.setField(merchant, "id", 7L);
        String description = "=Тест, \"строка\"\nпродолжение";
        var payment = new Payment(merchant, "+order", new BigDecimal("100.25"), description,
                true, Set.of("два, три", "один"));
        var output = new StringWriter();
        try (var printer = codec.printer(output)) {
            codec.write(printer, payment);
        }
        assertTrue(output.toString().contains("'+order"));
        assertTrue(output.toString().contains("'=Тест"));
        var parsed = read(output.toString());
        assertTrue(parsed.errors().isEmpty());
        var row = parsed.rows().getFirst();
        assertEquals(description, row.description());
        assertEquals("+order", row.orderNumber());
        assertEquals(Set.of("два, три", "один"), row.tags());
    }

    @Test
    void reports_bad_records_without_losing_valid_rows() {
        var parsed = read(HEADER + """
                7,ok,1.20,RUB,Годится,CREATED,false,[]
                7,bad,1.001,RUB,Не годится,CREATED,false,[]
                7,bad-tags,1.20,RUB,Не годится,CREATED,false,[1]
                """);
        assertEquals(1, parsed.rows().size());
        assertEquals(3, parsed.errors().getFirst().row());
        assertEquals(4, parsed.errors().getLast().row());
    }

    @Test
    void rejects_a_malformed_file() {
        assertThrows(ResponseStatusException.class, () -> read(HEADER + "7,\"unfinished"));
    }

    private PaymentCsvCodec.ParsedFile read(String input) {
        return codec.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }
}
