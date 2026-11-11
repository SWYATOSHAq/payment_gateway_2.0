package ru.university.paymentgateway.dto;

import java.util.UUID;

public record AdminUser(UUID id, String login, String displayName, Role role,
                        boolean active, boolean mustChangePassword) {
    public enum Role {
        VIEWER, EDITOR, ADMIN
    }
}
