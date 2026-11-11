package ru.university.paymentgateway.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.university.paymentgateway.dto.AuditEntry;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.filter.CorrelationIdFilter;

@Repository
public class AuditRepository {
    private static final String SEARCH = """
            FROM audit_events event
            LEFT JOIN user_accounts actor ON actor.id = event.actor_id
            WHERE :query = '' OR strpos(lower(concat_ws(' ', actor.login, event.event_type,
                event.entity_type, event.entity_id, event.description, event.correlation_id)), lower(:query)) > 0
            """;

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID actor, String event_type, String entity_type, UUID entity, String description) {
        jdbc.sql("""
                INSERT INTO audit_events (actor_id, event_type, entity_type, entity_id, description, correlation_id)
                VALUES (:actor, :event, :type, :entity, :description, :correlation)
                """)
                .param("actor", actor, Types.OTHER).param("event", event_type).param("type", entity_type)
                .param("entity", entity).param("description", description)
                .param("correlation", CorrelationIdFilter.current_id()).update();
    }

    public PageResponse<AuditEntry> list(int page, int size, String query, boolean ascending) {
        String order = ascending ? "ORDER BY event.occurred_at, event.id"
                : "ORDER BY event.occurred_at DESC, event.id DESC";
        var items = jdbc.sql("SELECT event.*, actor.login AS actor_login " + SEARCH + order
                        + " LIMIT :size OFFSET :offset")
                .param("query", query).param("size", size).param("offset", (long) page * size)
                .query((row, index) -> new AuditEntry(row.getObject("id", UUID.class),
                        row.getObject("actor_id", UUID.class), row.getString("actor_login"),
                        row.getString("event_type"), row.getString("entity_type"),
                        row.getObject("entity_id", UUID.class),
                        row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        row.getString("description"), row.getObject("correlation_id", UUID.class)))
                .list();
        long total = jdbc.sql("SELECT count(*) " + SEARCH).param("query", query).query(Long.class).single();
        return new PageResponse<>(items, page, size, total);
    }
}
