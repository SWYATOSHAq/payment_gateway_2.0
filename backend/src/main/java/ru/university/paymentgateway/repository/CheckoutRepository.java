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

    public Optional<CheckoutPayment> reserve(long merchant_id, String order_number, BigDecimal amount) {
        return jdbc.sql("""
                INSERT INTO payments (merchant_id, order_number, amount, currency, description, checkout_created_at)
                SELECT id, :order, :amount, 'RUB', :description, now() FROM merchants WHERE id = :merchant AND active
                ON CONFLICT (merchant_id, order_number) DO UPDATE SET order_number = EXCLUDED.order_number
                RETURNING uuid, amount, status, checkout_created_at, payment_url, payment_method
                """)
                .param("merchant", merchant_id).param("order", order_number).param("amount", amount)
                .param("description", "Заказ " + order_number)
                .query(CheckoutRepository::map_payment).optional();
    }

    public Optional<CheckoutPayment> find(UUID uuid) {
        return jdbc.sql("""
                SELECT uuid, amount, status, checkout_created_at, payment_url, payment_method
                FROM payments WHERE uuid = :uuid AND checkout_created_at IS NOT NULL
                """)
                .param("uuid", uuid).query(CheckoutRepository::map_payment).optional();
    }

    @Transactional
    public void save_link(UUID uuid, String url) {
        int updated = jdbc.sql("""
                UPDATE payments SET payment_url = :url, updated_at = now(), version = version + 1
                WHERE uuid = :uuid AND payment_url IS NULL
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
                    updated_at = now(), version = version + 1
                WHERE uuid = :uuid AND checkout_created_at IS NOT NULL
                    AND amount = :amount AND currency = 'RUB' AND status = 'CREATED'
                """)
                .param("uuid", uuid).param("amount", amount).param("transaction", transaction_id)
                .param("method", method).update();
        if (updated == 1) {
            audit.record(null, "PAYMENT_COMPLETED", "PAYMENT", uuid, "CloudPayments подтвердил оплату");
            return true;
        }
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM payments
                    WHERE uuid = :uuid AND amount = :amount AND status = 'COMPLETED'
                        AND provider_transaction_id = :transaction)
                """)
                .param("uuid", uuid).param("amount", amount).param("transaction", transaction_id)
                .query(Boolean.class).single();
    }

    private static CheckoutPayment map_payment(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var created = row.getObject("checkout_created_at", OffsetDateTime.class);
        return new CheckoutPayment(row.getObject("uuid", UUID.class), row.getBigDecimal("amount"),
                row.getString("status"), created == null ? null : created.toInstant(),
                row.getString("payment_url"), row.getString("payment_method"));
    }
}
