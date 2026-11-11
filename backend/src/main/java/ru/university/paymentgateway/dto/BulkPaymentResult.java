package ru.university.paymentgateway.dto;

import java.util.List;
import java.util.UUID;

public record BulkPaymentResult(List<Success> successes, List<Failure> errors) {
    public record Success(UUID id, long version) {}
    public record Failure(UUID id, int status, String message) {}
}
