package ru.university.paymentgateway.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.university.paymentgateway.dto.PaymentNoteView;

@Repository
public class PaymentNoteRepository {
    private static final String NOTES = """
            SELECT note.*, author.display_name AS author_name
            FROM payment_notes note JOIN user_accounts author ON author.id = note.author_id
            WHERE note.payment_id = :payment
            """;

    private final JdbcClient jdbc;

    public PaymentNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NotePayment> payment(UUID payment, boolean lock) {
        String sql = "SELECT id, archived FROM payments WHERE uuid = :uuid";
        return jdbc.sql(lock ? sql + " FOR UPDATE" : sql).param("uuid", payment)
                .query((row, index) -> new NotePayment(row.getLong("id"), row.getBoolean("archived"))).optional();
    }

    public List<PaymentNoteView> list(long payment) {
        return jdbc.sql(NOTES + " ORDER BY note.pinned DESC, note.created_at DESC, note.id DESC")
                .param("payment", payment).query(PaymentNoteRepository::map_note).list();
    }

    public Optional<PaymentNoteView> find(long payment, UUID id) {
        return jdbc.sql(NOTES + " AND note.id = :id").param("payment", payment).param("id", id)
                .query(PaymentNoteRepository::map_note).optional();
    }

    public UUID create(long payment, UUID author, String body, boolean pinned) {
        return jdbc.sql("""
                INSERT INTO payment_notes (payment_id, author_id, body, pinned)
                VALUES (:payment, :author, :body, :pinned)
                RETURNING id
                """).param("payment", payment).param("author", author)
                .param("body", body).param("pinned", pinned).query(UUID.class).single();
    }

    public void update(long payment, UUID id, String body, boolean pinned) {
        jdbc.sql("""
                UPDATE payment_notes SET body = :body, pinned = :pinned, updated_at = now()
                WHERE payment_id = :payment AND id = :id
                """).param("body", body).param("pinned", pinned)
                .param("payment", payment).param("id", id).update();
    }

    public void delete(long payment, UUID id) {
        jdbc.sql("DELETE FROM payment_notes WHERE payment_id = :payment AND id = :id")
                .param("payment", payment).param("id", id).update();
    }

    private static PaymentNoteView map_note(ResultSet row, int index) throws SQLException {
        return new PaymentNoteView(row.getObject("id", UUID.class), row.getObject("author_id", UUID.class),
                row.getString("author_name"), row.getString("body"), row.getBoolean("pinned"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    public record NotePayment(long id, boolean archived) {}
}
