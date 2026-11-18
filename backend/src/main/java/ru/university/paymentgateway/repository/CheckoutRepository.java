package ru.university.paymentgateway.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.university.paymentgateway.domain.CheckoutPayment;

@Repository
public class CheckoutRepository {
    private final JdbcClient jdbc;

    private final AuditRepository audit;

    public CheckoutRepository(JdbcClient jdbc, AuditRepository audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public Optional<CheckoutPayment> reserve(long merchant_id, String order_number, BigDecimal amount, boolean embedded, String provider) {
        var inserted = jdbc.sql("""
                INSERT INTO payments (merchant_id, order_number, amount, currency, description, checkout_created_at, embedded_checkout, checkout_provider)
                SELECT id, :order, :amount, 'RUB', :description, now(), :embedded, :provider FROM merchants WHERE id = :merchant AND active
                ON CONFLICT (merchant_id, order_number) DO NOTHING
                RETURNING uuid, amount, status, checkout_created_at, payment_url, payment_method, embedded_checkout, checkout_provider
                """)
                .param("merchant", merchant_id).param("order", order_number).param("amount", amount)
                .param("description", "Заказ " + order_number).param("embedded", embedded).param("provider", provider)
                .query(CheckoutRepository::map_payment).optional();
        if (inserted.isPresent()) {
            if (embedded) {
                audit.record(null, "EMBEDDED_CHECKOUT_CREATED", "PAYMENT", inserted.get().uuid(),
                        "Создан платёж для встроенной формы");
            }
            return inserted;
        }
        return jdbc.sql("""
                SELECT p.uuid, p.amount, p.status, p.checkout_created_at, p.payment_url, p.payment_method, p.embedded_checkout, p.checkout_provider
                FROM payments p JOIN merchants m ON m.id = p.merchant_id
                WHERE p.merchant_id = :merchant AND p.order_number = :order AND m.active
                """).param("merchant", merchant_id).param("order", order_number)
                .query(CheckoutRepository::map_payment).optional();
    }

    public Optional<CheckoutPayment> find(UUID uuid) {
        return jdbc.sql("""
                SELECT uuid, amount, status, checkout_created_at, payment_url, payment_method, embedded_checkout, checkout_provider
                FROM payments WHERE uuid = :uuid AND checkout_provider = 'CLOUDPAYMENTS'
                """)
                .param("uuid", uuid).query(CheckoutRepository::map_payment).optional();
    }

    @Transactional
    public void save_link(UUID uuid, String url) {
        int updated = jdbc.sql("""
                UPDATE payments SET payment_url = :url, updated_at = now(), version = version + 1
                WHERE uuid = :uuid AND checkout_provider = 'CLOUDPAYMENTS' AND payment_url IS NULL AND NOT embedded_checkout
                """)
                .param("uuid", uuid).param("url", url).update();
        if (updated == 1) {
            audit.record(null, "PAYMENT_LINK_CREATED", "PAYMENT", uuid, "Создана ссылка на оплату");
        }
    }

    @Transactional
    public boolean complete(UUID uuid, BigDecimal amount, long transaction_id, String method) {
        int updated = jdbc.sql("""
                UPDATE payments
                SET status = 'COMPLETED', provider_transaction_id = :transaction, payment_method = :method,
                    checked_transaction_id = NULL,
                    updated_at = now(), version = version + 1
                WHERE uuid = :uuid AND checkout_provider = 'CLOUDPAYMENTS'
                    AND amount = :amount AND currency = 'RUB' AND status = 'CREATED'
                    AND (NOT embedded_checkout OR checked_transaction_id = :transaction)
                """)
                .param("uuid", uuid).param("amount", amount).param("transaction", transaction_id)
                .param("method", method).update();
        if (updated == 1) {
            audit.record(null, "PAYMENT_COMPLETED", "PAYMENT", uuid, "CloudPayments подтвердил оплату");
            return true;
        }
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM payments
                    WHERE uuid = :uuid AND checkout_provider = 'CLOUDPAYMENTS' AND amount = :amount AND status = 'COMPLETED'
                        AND provider_transaction_id = :transaction)
                """)
                .param("uuid", uuid).param("amount", amount).param("transaction", transaction_id)
                .query(Boolean.class).single();
    }

    public Optional<EmbeddedPayment> embedded(UUID id) {
        return jdbc.sql("""
                SELECT p.uuid, p.order_number, m.name, p.description, p.amount, p.status, p.payment_method,
                    m.active AND NOT p.archived AS available, p.checked_transaction_id IS NOT NULL AS processing
                FROM payments p JOIN merchants m ON m.id = p.merchant_id
                WHERE p.uuid = :id AND p.checkout_provider = 'CLOUDPAYMENTS' AND p.embedded_checkout
                """).param("id", id).query((row, index) -> new EmbeddedPayment(row.getObject("uuid", UUID.class),
                        row.getString("order_number"), row.getString("name"), row.getString("description"),
                        row.getBigDecimal("amount"), row.getString("status"), row.getString("payment_method"),
                        row.getBoolean("available"), row.getBoolean("processing"))).optional();
    }

    @Transactional
    public int check(UUID id, BigDecimal amount, long transaction, boolean embedded_enabled) {
        var candidate = jdbc.sql("""
                SELECT p.amount, p.status, p.embedded_checkout, p.checked_transaction_id,
                    m.active AND NOT p.archived AS available
                FROM payments p JOIN merchants m ON m.id = p.merchant_id
                WHERE p.uuid = :id AND p.checkout_provider = 'CLOUDPAYMENTS'
                FOR UPDATE OF p
                """).param("id", id).query((row, index) -> new CheckPayment(row.getBigDecimal("amount"),
                        row.getString("status"), row.getBoolean("embedded_checkout"),
                        row.getObject("checked_transaction_id", Long.class), row.getBoolean("available")))
                .optional();
        if (candidate.isEmpty()) {
            return 10;
        }
        var payment = candidate.get();
        if (payment.amount().compareTo(amount) != 0) {
            return 12;
        }
        if (!payment.available() || !"CREATED".equals(payment.status()) || (payment.embedded() && !embedded_enabled)
                || (payment.transaction() != null && payment.transaction() != transaction)) {
            return 13;
        }
        if (payment.embedded() && payment.transaction() == null) {
            jdbc.sql("UPDATE payments SET checked_transaction_id = :transaction WHERE uuid = :id")
                    .param("transaction", transaction).param("id", id).update();
        }
        return 0;
    }

    public void release(UUID id, BigDecimal amount, long transaction) {
        jdbc.sql("""
                UPDATE payments SET checked_transaction_id = NULL
                WHERE uuid = :id AND checkout_provider = 'CLOUDPAYMENTS' AND amount = :amount AND embedded_checkout
                    AND checked_transaction_id = :transaction
                """).param("id", id).param("amount", amount).param("transaction", transaction).update();
    }

    public Optional<YooKassaPayment> yookassa(UUID id) {
        return jdbc.sql("""
                SELECT p.uuid, p.order_number, m.name, p.description, p.amount, p.status, p.payment_method,
                    p.external_payment_id, p.confirmation_token, m.active AND NOT p.archived AS available
                FROM payments p JOIN merchants m ON m.id = p.merchant_id
                WHERE p.uuid = :id AND p.checkout_provider = 'YOOKASSA'
                """).param("id", id).query((row, index) -> new YooKassaPayment(
                        row.getObject("uuid", UUID.class), row.getString("order_number"), row.getString("name"),
                        row.getString("description"), row.getBigDecimal("amount"), row.getString("status"),
                        row.getString("payment_method"), row.getString("external_payment_id"),
                        row.getString("confirmation_token"), row.getBoolean("available"))).optional();
    }

    public void save_yookassa(UUID id, String external_id, String token) {
        jdbc.sql("""
                UPDATE payments
                SET external_payment_id = :external, confirmation_token = :token,
                    updated_at = now(), version = version + 1
                WHERE uuid = :id AND checkout_provider = 'YOOKASSA' AND external_payment_id IS NULL
                """).param("id", id).param("external", external_id).param("token", token).update();
    }

    @Transactional
    public void finish_yookassa(UUID id, String external_id, boolean paid) {
        String status = paid ? "COMPLETED" : "CANCELLED";
        int updated = jdbc.sql("""
                UPDATE payments
                SET status = :status, payment_method = :method, confirmation_token = NULL,
                    updated_at = now(), version = version + 1
                WHERE uuid = :id AND checkout_provider = 'YOOKASSA' AND external_payment_id = :external
                    AND status = 'CREATED'
                """).param("id", id).param("external", external_id).param("status", status)
                .param("method", paid ? "bank_card" : null).update();
        if (updated == 1) {
            audit.record(null, "PAYMENT_" + status, "PAYMENT", id,
                    paid ? "ЮKassa подтвердила оплату" : "ЮKassa отменила платёж");
        }
    }

    public record YooKassaPayment(UUID id, String order_number, String merchant_name, String description,
                                 BigDecimal amount, String status, String payment_method, String external_id,
                                 String token, boolean available) {}

    public record EmbeddedPayment(UUID id, String order_number, String merchant_name, String description,
                                  BigDecimal amount, String status, String payment_method, boolean available,
                                  boolean processing) {}

    private record CheckPayment(BigDecimal amount, String status, boolean embedded, Long transaction, boolean available) {}

    private static CheckoutPayment map_payment(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var created = row.getObject("checkout_created_at", OffsetDateTime.class);
        return new CheckoutPayment(row.getObject("uuid", UUID.class), row.getBigDecimal("amount"),
                row.getString("status"), created == null ? null : created.toInstant(),
                row.getString("payment_url"), row.getString("payment_method"), row.getBoolean("embedded_checkout"),
                row.getString("checkout_provider"));
    }
}
