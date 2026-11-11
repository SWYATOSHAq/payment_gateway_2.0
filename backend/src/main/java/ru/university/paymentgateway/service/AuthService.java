package ru.university.paymentgateway.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Account;
import ru.university.paymentgateway.dto.AuthResponse;
import ru.university.paymentgateway.dto.UserProfile;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.AuthRepository;
import ru.university.paymentgateway.security.AccessTokens;

@Service
public class AuthService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(5);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(14);

    private final AuthRepository accounts;
    private final AuditRepository audit;
    private final PasswordEncoder passwords;
    private final AccessTokens access_tokens;
    private final String dummy_hash;

    public AuthService(AuthRepository accounts, AuditRepository audit, PasswordEncoder passwords, AccessTokens access_tokens) {
        this.accounts = accounts;
        this.audit = audit;
        this.passwords = passwords;
        this.access_tokens = access_tokens;
        this.dummy_hash = passwords.encode(AccessTokens.new_refresh_token());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse login(String login, String password) {
        if (!bcrypt_length(password)) {
            throw unauthorized();
        }
        var account = accounts.lock_by_login(normalize_login(login)).orElse(null);
        Instant now = Instant.now();
        if (account == null || !account.active()
                || (account.locked_until() != null && account.locked_until().isAfter(now))) {
            passwords.matches(password, dummy_hash);
            throw unauthorized();
        }
        if (!passwords.matches(password, account.password_hash())) {
            int failures = account.locked_until() == null ? account.failed_attempts() + 1 : 1;
            accounts.record_attempt(account.id(), failures, failures >= MAX_ATTEMPTS ? now.plus(LOCK_TIME) : null);
            throw unauthorized();
        }
        accounts.record_attempt(account.id(), 0, null);
        audit.record(account.id(), "LOGIN_SUCCEEDED", "USER", account.id(), "Вход выполнен");
        return create_session(account);
    }

    @Transactional
    public AuthResponse refresh(String token) {
        String next_token = AccessTokens.new_refresh_token();
        UUID session = accounts.rotate_session(hash_token(token), hash_token(next_token))
                .orElseThrow(AuthService::unauthorized);
        return issue(account_for_session(session), session, next_token);
    }

    @Transactional
    public AuthResponse change_password(UUID session_id, String current, String replacement) {
        Account user = account_for_session(session_id);
        Account locked = accounts.lock_by_id(user.id()).filter(Account::active)
                .orElseThrow(AuthService::unauthorized);
        account_for_session(session_id);
        if (!bcrypt_length(current) || !passwords.matches(current, locked.password_hash())) {
            throw unauthorized();
        }
        validate_new_password(replacement);
        if (current.equals(replacement)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль должен отличаться от текущего");
        }
        accounts.change_password(user.id(), passwords.encode(replacement));
        audit.record(user.id(), "PASSWORD_CHANGED", "USER", user.id(), "Пароль изменён");
        return create_session(accounts.lock_by_id(user.id()).orElseThrow());
    }

    public UserProfile profile(UUID session_id) {
        return UserProfile.from(account_for_session(session_id));
    }

    public void logout(UUID session_id) {
        accounts.revoke_session(session_id);
    }

    public static String normalize_login(String login) {
        return login.strip().toLowerCase(Locale.ROOT);
    }

    public static void validate_new_password(String password) {
        if (!bcrypt_length(password) || password.length() < 12 || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен содержать от 12 символов и занимать не более 72 байт");
        }
    }

    private Account account_for_session(UUID session_id) {
        return accounts.session_account(session_id).orElseThrow(AuthService::unauthorized);
    }

    private AuthResponse create_session(Account account) {
        String token = AccessTokens.new_refresh_token();
        UUID session = UUID.randomUUID();
        accounts.create_session(session, account.id(), hash_token(token), Instant.now().plus(REFRESH_LIFETIME));
        return issue(account, session, token);
    }

    private AuthResponse issue(Account account, UUID session, String token) {
        return new AuthResponse(access_tokens.issue(account.id(), session), token,
                AccessTokens.LIFETIME.toSeconds(), UserProfile.from(account));
    }

    private static boolean bcrypt_length(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static String hash_token(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учётные данные или сессия истекла");
    }
}
