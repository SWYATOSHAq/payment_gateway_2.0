package ru.university.paymentgateway.dto;

import java.util.List;

public record CsvImportReport(int validRows, int invalidRows, int created, int updated, List<RowError> errors) {
    public enum Mode { CREATE_ONLY, UPSERT }

    public record RowError(long row, String message) {}
}
