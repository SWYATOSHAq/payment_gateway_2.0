package ru.university.paymentgateway.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentNoteView(UUID id, UUID authorId, String authorName, String text,
                              boolean pinned, Instant createdAt, Instant updatedAt) {}
