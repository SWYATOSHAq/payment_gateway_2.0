package ru.university.paymentgateway.service;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.dto.AdminUser;
import ru.university.paymentgateway.dto.AdminUser.Role;
import ru.university.paymentgateway.dto.AuditEntry;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.repository.AdminRepository;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.AuthRepository;

@Service
public class AdminService {
    private final AdminRepository users;
    private final AuthRepository sessions;
    private final AuditRepository audit;
    private final PasswordEncoder passwords;

    public AdminService(AdminRepository users, AuthRepository sessions, AuditRepository audit,
                        PasswordEncoder passwords) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.passwords = passwords;
    }

    public PageResponse<AdminUser> list(int page, int size) {
        return users.list(page, size);
    }

    public PageResponse<AuditEntry> list_audit(int page, int size, String query, boolean ascending) {
        return audit.list(page, size, query.strip(), ascending);
    }

    @Transactional
    public AdminUser create(UUID actor, String login, String name, Role role, String temporary_password) {
        AuthService.validate_new_password(temporary_password);
        try {
            AdminUser user = users.create(normalize_login(login), name.strip(), role, passwords.encode(temporary_password));
            audit.record(actor, "USER_CREATED", "USER", user.id(), "Создана учётная запись");
            return user;
        } catch (DuplicateKeyException exception) {
            throw duplicate_login();
        }
    }

    @Transactional
    public AdminUser update(UUID actor, UUID id, String login, String name, Role role, boolean active) {
        if (actor.equals(id) && (!active || role != Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя отключить себя или снять с себя роль администратора");
        }
        try {
            AdminUser user = users.update(id, normalize_login(login), name.strip(), role, active)
                    .orElseThrow(AdminService::user_not_found);
            if (!active) {
                sessions.revoke_user_sessions(id);
            }
            audit.record(actor, "USER_UPDATED", "USER", id, "Изменены данные учётной записи");
            return user;
        } catch (DuplicateKeyException exception) {
            throw duplicate_login();
        }
    }

    @Transactional
    public AdminUser reset_password(UUID actor, UUID id, String temporary_password) {
        AuthService.validate_new_password(temporary_password);
        AdminUser user = users.reset_password(id, passwords.encode(temporary_password))
                .orElseThrow(AdminService::user_not_found);
        sessions.revoke_user_sessions(id);
        audit.record(actor, "PASSWORD_RESET", "USER", id, "Установлен временный пароль");
        return user;
    }

    private static String normalize_login(String login) {
        String normalized = AuthService.normalize_login(login);
        if (normalized.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Логин слишком длинный");
        }
        return normalized;
    }

    private static ResponseStatusException user_not_found() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
    }

    private static ResponseStatusException duplicate_login() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Логин уже занят");
    }
}
