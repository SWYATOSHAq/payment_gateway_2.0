package ru.university.paymentgateway.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(UUID id, UUID actorId, String actorLogin, String eventType,
                         String entityType, UUID entityId, Instant occurredAt,
                         String description, UUID correlationId) {}
