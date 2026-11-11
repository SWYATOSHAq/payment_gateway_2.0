package ru.university.paymentgateway.domain;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, String login, String display_name, String password_hash,
                      String role, boolean active, boolean must_change_password,
                      int failed_attempts, Instant locked_until) {}
