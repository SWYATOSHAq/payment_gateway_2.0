package ru.university.paymentgateway.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.university.paymentgateway.dto.AdminUser;
import ru.university.paymentgateway.dto.AdminUser.Role;
import ru.university.paymentgateway.dto.PageResponse;

@Repository
public class AdminRepository {
    private static final String USER_FIELDS = "id, login, display_name, role, active, must_change_password";
    private final JdbcClient jdbc;

    public AdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<AdminUser> list(int page, int size) {
        var items = jdbc.sql("SELECT " + USER_FIELDS + " FROM user_accounts ORDER BY login LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query(AdminRepository::map_user).list();
        long total = jdbc.sql("SELECT count(*) FROM user_accounts").query(Long.class).single();
        return new PageResponse<>(items, page, size, total);
    }

    public AdminUser create(String login, String name, Role role, String hash) {
        return jdbc.sql("""
                INSERT INTO user_accounts (login, display_name, role, password_hash)
                VALUES (:login, :name, :role, :hash)
                RETURNING
                """ + USER_FIELDS)
                .param("login", login).param("name", name).param("role", role.name()).param("hash", hash)
                .query(AdminRepository::map_user).single();
    }

    public Optional<AdminUser> update(UUID id, String login, String name, Role role, boolean active) {
        return jdbc.sql("""
                UPDATE user_accounts SET login = :login, display_name = :name, role = :role, active = :active
                WHERE id = :id
                RETURNING
                """ + USER_FIELDS)
                .param("id", id).param("login", login).param("name", name).param("role", role.name())
                .param("active", active).query(AdminRepository::map_user).optional();
    }

    public Optional<AdminUser> reset_password(UUID id, String hash) {
        return jdbc.sql("""
                UPDATE user_accounts SET password_hash = :hash, must_change_password = TRUE,
                    failed_attempts = 0, locked_until = NULL
                WHERE id = :id
                RETURNING
                """ + USER_FIELDS)
                .param("id", id).param("hash", hash).query(AdminRepository::map_user).optional();
    }

    private static AdminUser map_user(ResultSet row, int index) throws SQLException {
        return new AdminUser(row.getObject("id", UUID.class), row.getString("login"),
                row.getString("display_name"), Role.valueOf(row.getString("role")),
                row.getBoolean("active"), row.getBoolean("must_change_password"));
    }
}
