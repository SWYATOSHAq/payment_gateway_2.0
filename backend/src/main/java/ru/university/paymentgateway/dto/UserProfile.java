package ru.university.paymentgateway.dto;

import java.util.UUID;
import ru.university.paymentgateway.domain.Account;

public record UserProfile(UUID id, String login, String displayName, String role, boolean mustChangePassword) {
    public static UserProfile from(Account account) {
        return new UserProfile(account.id(), account.login(), account.display_name(),
                account.role(), account.must_change_password());
    }
}
