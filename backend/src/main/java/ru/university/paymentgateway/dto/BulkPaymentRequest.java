package ru.university.paymentgateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import ru.university.paymentgateway.domain.Payment.Status;

public record BulkPaymentRequest(@NotNull Operation operation,
                                 @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items,
                                 Status status, @Size(max = 40) String tag) {
    public enum Operation {
        ARCHIVE, RESTORE, CHANGE_STATUS, ADD_TAG, REMOVE_TAG
    }

    public record Item(@NotNull UUID id, @NotNull @PositiveOrZero Long version) {}
}
