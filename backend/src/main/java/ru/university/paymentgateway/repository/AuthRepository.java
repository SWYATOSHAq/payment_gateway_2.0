package ru.university.paymentgateway.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.university.paymentgateway.domain.Account;

@Repository
public class AuthRepository {
    private final JdbcClient jdbc;

    public AuthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void bootstrap_admin(String login, String display_name, String password_hash) {
        jdbc.sql("""
                INSERT INTO user_accounts (login, display_name, password_hash, role)
                VALUES (:login, :name, :hash, 'ADMIN')
                ON CONFLICT (login) DO NOTHING
                """)
                .param("login", login).param("name", display_name).param("hash", password_hash).update();
    }

    public Optional<Account> lock_by_login(String login) {
        return jdbc.sql("SELECT * FROM user_accounts WHERE login = :login FOR UPDATE")
                .param("login", login).query(AuthRepository::map_account).optional();
    }

    public Optional<Account> lock_by_id(UUID user_id) {
        return jdbc.sql("SELECT * FROM user_accounts WHERE id = :id FOR UPDATE")
                .param("id", user_id).query(AuthRepository::map_account).optional();
    }

    public void record_attempt(UUID user_id, int failures, Instant locked_until) {
        jdbc.sql("""
                UPDATE user_accounts SET failed_attempts = :failures, locked_until = :locked_until
                WHERE id = :id
                """)
                .param("failures", failures).param("locked_until", timestamp(locked_until))
                .param("id", user_id).update();
    }

    public void change_password(UUID user_id, String password_hash) {
        jdbc.sql("""
                UPDATE user_accounts SET password_hash = :hash, must_change_password = FALSE WHERE id = :id
                """)
                .param("hash", password_hash).param("id", user_id).update();
        revoke_user_sessions(user_id);
    }

    public void revoke_user_sessions(UUID user_id) {
        jdbc.sql("DELETE FROM refresh_sessions WHERE user_id = :id").param("id", user_id).update();
    }

    public void create_session(UUID id, UUID user_id, String hash, Instant expires_at) {
        jdbc.sql("DELETE FROM refresh_sessions WHERE user_id = :user AND expires_at <= now()")
                .param("user", user_id).update();
        jdbc.sql("""
                INSERT INTO refresh_sessions (id, user_id, token_hash, expires_at)
                VALUES (:id, :user, :hash, :expires_at)
                """)
                .param("id", id).param("user", user_id).param("hash", hash)
                .param("expires_at", timestamp(expires_at)).update();
    }

    public Optional<UUID> rotate_session(String current_hash, String new_hash) {
        return jdbc.sql("""
                UPDATE refresh_sessions AS session SET token_hash = :new_hash
                FROM user_accounts AS account
                WHERE session.user_id = account.id AND account.active
                    AND session.token_hash = :current_hash AND session.expires_at > now()
                RETURNING session.id
                """)
                .param("current_hash", current_hash).param("new_hash", new_hash)
                .query(UUID.class).optional();
    }

    public Optional<Account> session_account(UUID session_id) {
        return jdbc.sql("""
                SELECT account.* FROM user_accounts AS account
                JOIN refresh_sessions AS session ON session.user_id = account.id
                WHERE session.id = :session AND session.expires_at > now() AND account.active
                """)
                .param("session", session_id).query(AuthRepository::map_account).optional();
    }

    public void revoke_session(UUID session_id) {
        jdbc.sql("DELETE FROM refresh_sessions WHERE id = :id").param("id", session_id).update();
    }

    private static OffsetDateTime timestamp(Instant time) {
        return time == null ? null : time.atOffset(ZoneOffset.UTC);
    }

    private static Account map_account(ResultSet row, int index) throws SQLException {
        OffsetDateTime locked = row.getObject("locked_until", OffsetDateTime.class);
        return new Account(row.getObject("id", UUID.class), row.getString("login"),
                row.getString("display_name"), row.getString("password_hash"), row.getString("role"),
                row.getBoolean("active"), row.getBoolean("must_change_password"), row.getInt("failed_attempts"),
                locked == null ? null : locked.toInstant());
    }
}
