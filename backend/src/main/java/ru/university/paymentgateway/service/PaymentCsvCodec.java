package ru.university.paymentgateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Payment;
import ru.university.paymentgateway.domain.Payment.Status;
import ru.university.paymentgateway.dto.CsvImportReport.RowError;
import ru.university.paymentgateway.filter.PaymentFilter;

@Component
class PaymentCsvCodec {
    private static final int MAX_ROWS = 500;
    private static final List<String> COLUMNS = List.of("merchantId", "orderNumber", "amount", "currency",
            "description", "status", "needsReview", "tags");
    private static final CSVFormat INPUT = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
    private static final CSVFormat OUTPUT = CSVFormat.RFC4180.builder().setHeader(COLUMNS.toArray(String[]::new)).get();

    private final ObjectMapper json;

    PaymentCsvCodec(ObjectMapper json) {
        this.json = json;
    }

    ParsedFile read(InputStream input) {
        var rows = new ArrayList<PaymentRow>();
        var errors = new ArrayList<RowError>();
        try (var reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()))) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') {
                reader.unread(first);
            }
            try (var parser = INPUT.parse(reader)) {
                if (!Set.copyOf(parser.getHeaderNames()).equals(Set.copyOf(COLUMNS))) {
                    throw bad_request("Ожидаемые колонки CSV: " + String.join(", ", COLUMNS));
                }
                for (CSVRecord record : parser) {
                    if (rows.size() + errors.size() == MAX_ROWS) {
                        throw bad_request("CSV может содержать не более 500 строк данных");
                    }
                    try {
                        rows.add(parse_row(record));
                    } catch (IllegalArgumentException exception) {
                        errors.add(new RowError(record.getRecordNumber() + 1, exception.getMessage()));
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            throw bad_request("Не удалось прочитать CSV в UTF-8. Проверьте заголовки и кавычки");
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            throw bad_request("CSV не содержит строк данных");
        }
        return new ParsedFile(rows, errors);
    }

    CSVPrinter printer(Writer writer) throws IOException {
        writer.write('\uFEFF');
        return new CSVPrinter(writer, OUTPUT);
    }

    void write(CSVPrinter printer, Payment payment) throws IOException {
        printer.printRecord(payment.merchant().id(), escape_cell(payment.order_number()), payment.amount().toPlainString(),
                payment.currency(), escape_cell(payment.description()), payment.status(), payment.needs_review(),
                json.writeValueAsString(payment.tags().stream().sorted().toList()));
    }

    private PaymentRow parse_row(CSVRecord record) {
        if (!record.isConsistent()) {
            throw invalid("Число полей не совпадает с заголовком");
        }
        String order = text(unescape_cell(record.get("orderNumber")), 100, "orderNumber");
        String description = text(unescape_cell(record.get("description")), 500, "description");
        if (!"RUB".equals(record.get("currency").strip())) {
            throw invalid("Валюта должна быть RUB");
        }
        String amount = record.get("amount").strip();
        if (!amount.matches("[0-9]{1,10}(\\.[0-9]{1,2})?") || new BigDecimal(amount).signum() <= 0) {
            throw invalid("Укажите положительную сумму с точностью до двух знаков");
        }
        String review = record.get("needsReview").strip();
        if (!"true".equals(review) && !"false".equals(review)) {
            throw invalid("needsReview должен быть true или false");
        }
        return new PaymentRow(record.getRecordNumber() + 1, merchant_id(record.get("merchantId")), order,
                new BigDecimal(amount), description, status(record.get("status")), Boolean.parseBoolean(review),
                tags(record.get("tags")));
    }

    private Set<String> tags(String value) {
        if (value.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode array = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            if (array == null || !array.isArray() || array.size() > 20) {
                throw invalid("tags должен быть JSON-массивом из не более 20 тегов");
            }
            Set<String> result = new HashSet<>();
            for (JsonNode tag : array) {
                if (!tag.isTextual()) {
                    throw invalid("Теги должны быть строками");
                }
                result.add(text(PaymentFilter.normalize_tag(tag.asText()), 40, "tag"));
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw invalid("tags должен быть JSON-массивом строк");
        }
    }

    private static long merchant_id(String value) {
        try {
            long id = Long.parseLong(value.strip());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Use the same row error for malformed or out-of-range IDs.
        }
        throw invalid("merchantId должен быть положительным целым числом");
    }

    private static Status status(String value) {
        try {
            return Status.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            throw invalid("status должен быть CREATED, COMPLETED или CANCELLED");
        }
    }

    private static String text(String raw, int max, String field) {
        String value = raw.strip();
        if (value.isEmpty() || value.length() > max || value.indexOf('\0') >= 0) {
            throw invalid("Проверьте поле " + field + ": допустимо от 1 до " + max + " символов");
        }
        return value;
    }

    private static String escape_cell(String value) {
        return value != null && needs_escape(value) ? "'" + value : value;
    }

    private static String unescape_cell(String value) {
        return value.startsWith("'") && needs_escape(value.substring(1)) ? value.substring(1) : value;
    }

    private static boolean needs_escape(String value) {
        String trimmed = value.stripLeading();
        return value.startsWith("'") || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")
                || (!trimmed.isEmpty() && "=+-@＝＋－＠".indexOf(trimmed.charAt(0)) >= 0);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static ResponseStatusException bad_request(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    record PaymentRow(long row, long merchantId, String orderNumber, BigDecimal amount, String description,
                      Status status, boolean needsReview, Set<String> tags) {}
    record ParsedFile(List<PaymentRow> rows, List<RowError> errors) {}
}
