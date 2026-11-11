package ru.university.paymentgateway.repository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.university.paymentgateway.domain.AmountCategory;
import ru.university.paymentgateway.domain.Payment.Status;
import ru.university.paymentgateway.dto.PaymentAnalyticsSummary.MerchantCount;

@Repository
public class PaymentAnalyticsRepository {
    private final JdbcClient jdbc;

    public PaymentAnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals() {
        return jdbc.sql("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE NOT archived) AS active,
                    count(*) FILTER (WHERE archived) AS archived,
                    count(*) FILTER (WHERE needs_review) AS needs_review,
                    coalesce(round(avg(amount), 2), 0.00) AS average_amount
                FROM payments
                """).query((row, index) -> new Totals(row.getLong("total"), row.getLong("active"),
                        row.getLong("archived"), row.getLong("needs_review"), row.getBigDecimal("average_amount")))
                .single();
    }

    public List<MerchantCount> by_merchant() {
        return jdbc.sql("""
                SELECT merchant.id, merchant.name, count(*) AS payment_count
                FROM payments payment JOIN merchants merchant ON merchant.id = payment.merchant_id
                GROUP BY merchant.id, merchant.name
                ORDER BY payment_count DESC, merchant.name, merchant.id
                """).query((row, index) -> new MerchantCount(
                        row.getLong("id"), row.getString("name"), row.getLong("payment_count"))).list();
    }

    public Map<Status, Long> by_status() {
        var counts = new EnumMap<Status, Long>(Status.class);
        for (Status status : Status.values()) {
            counts.put(status, 0L);
        }
        jdbc.sql("SELECT status, count(*) AS payment_count FROM payments GROUP BY status")
                .query((row, index) -> Map.entry(Status.valueOf(row.getString("status")), row.getLong("payment_count")))
                .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public Map<AmountCategory, Long> by_amount_category() {
        var counts = new EnumMap<AmountCategory, Long>(AmountCategory.class);
        for (AmountCategory category : AmountCategory.values()) {
            counts.put(category, 0L);
        }
        jdbc.sql("""
                SELECT CASE WHEN amount < :medium THEN 'SMALL'
                    WHEN amount < :large THEN 'MEDIUM' ELSE 'LARGE' END AS category,
                    count(*) AS payment_count
                FROM payments GROUP BY 1
                """).param("medium", AmountCategory.MEDIUM_FROM).param("large", AmountCategory.LARGE_FROM)
                .query((row, index) -> Map.entry(AmountCategory.valueOf(row.getString("category")), row.getLong("payment_count")))
                .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public record Totals(long total, long active, long archived, long needs_review, BigDecimal average_amount) {}
}
