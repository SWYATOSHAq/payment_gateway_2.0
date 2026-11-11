package ru.university.paymentgateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.university.paymentgateway.config.OpenApiConfiguration;
import ru.university.paymentgateway.domain.Payment.Status;
import ru.university.paymentgateway.dto.BulkPaymentRequest;
import ru.university.paymentgateway.dto.BulkPaymentResult;
import ru.university.paymentgateway.dto.CsvImportReport.Mode;
import ru.university.paymentgateway.dto.CsvImportReport;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.dto.PaymentView;
import ru.university.paymentgateway.filter.PaymentFilter;
import ru.university.paymentgateway.service.BulkPaymentService;
import ru.university.paymentgateway.service.PaymentCabinetService;
import ru.university.paymentgateway.service.PaymentCsvService;

@Tag(name = "Кабинет платежей")
@SecurityRequirement(name = OpenApiConfiguration.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/cabinet/payments")
public class PaymentCabinetController {
    private final PaymentCabinetService payments;

    private final BulkPaymentService bulk;
    private final PaymentCsvService csv;

    public PaymentCabinetController(PaymentCabinetService payments, BulkPaymentService bulk, PaymentCsvService csv) {
        this.payments = payments;
        this.bulk = bulk;
        this.csv = csv;
    }

    @PostMapping("/bulk")
    @Operation(operationId = "bulkUpdatePayments", summary = "Применить массовую операцию")
    public BulkPaymentResult bulk(JwtAuthenticationToken token, @Valid @RequestBody BulkPaymentRequest request) {
        return bulk.execute(actor(token), request);
    }

    @GetMapping
    @Operation(operationId = "listPayments", summary = "Список платежей")
    public PageResponse<PaymentView> list(@Valid @ParameterObject @ModelAttribute PaymentFilter filter,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                          @RequestParam(defaultValue = "CREATED_AT") OrderBy sort,
                                          @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return payments.list(filter, page, size, sort.to_sort(direction));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @Operation(operationId = "exportPaymentsCsv", summary = "Скачать CSV по фильтрам")
    @ApiResponse(responseCode = "200", description = "CSV-файл в UTF-8",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<StreamingResponseBody> export(@Valid @ParameterObject @ModelAttribute PaymentFilter filter,
                                                         @RequestParam(defaultValue = "CREATED_AT") OrderBy sort,
                                                         @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        StreamingResponseBody body = output -> csv.export(filter, sort.to_sort(direction), output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "previewPaymentsCsv", summary = "Проверить CSV перед импортом",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = CsvUpload.class))))
    public CsvImportReport preview(@Parameter(hidden = true) @RequestParam MultipartFile file,
                                   @Parameter(hidden = true) @RequestParam Mode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "applyPaymentsCsv", summary = "Применить импорт CSV",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = CsvUpload.class))))
    @ApiResponse(responseCode = "200", description = "Результат импорта",
            content = @Content(schema = @Schema(implementation = CsvImportReport.class)))
    @ApiResponse(responseCode = "422", description = "CSV содержит ошибочные строки",
            content = @Content(schema = @Schema(implementation = CsvImportReport.class)))
    public ResponseEntity<CsvImportReport> apply(JwtAuthenticationToken token,
                                                 @Parameter(hidden = true) @RequestParam MultipartFile file,
                                                 @Parameter(hidden = true) @RequestParam Mode mode) {
        CsvImportReport report = csv.apply(actor(token), file, mode);
        return ResponseEntity.status(report.invalidRows() == 0 ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(report);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPayment", summary = "Карточка платежа")
    public PaymentView get(@PathVariable UUID id) {
        return payments.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createPayment", summary = "Создать ручной платёж")
    public PaymentView create(JwtAuthenticationToken token, @Valid @RequestBody CreatePayment request) {
        return payments.create(actor(token), request.merchantId(), request.orderNumber(), request.amount(),
                request.description(), Boolean.TRUE.equals(request.needsReview()), request.tags());
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updatePayment", summary = "Изменить платёж")
    public PaymentView update(JwtAuthenticationToken token, @PathVariable UUID id,
                              @Valid @RequestBody UpdatePayment request) {
        return payments.update(actor(token), id, request.version(), request.amount(),
                request.description(), request.status(), request.needsReview(), request.tags());
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "archivePayment", summary = "Архивировать платёж")
    public PaymentView archive(JwtAuthenticationToken token, @PathVariable UUID id,
                               @RequestParam @PositiveOrZero long version) {
        return payments.archive(actor(token), id, version, true);
    }

    @PostMapping("/{id}/restore")
    @Operation(operationId = "restorePayment", summary = "Восстановить платёж")
    public PaymentView restore(JwtAuthenticationToken token, @PathVariable UUID id,
                               @RequestParam @PositiveOrZero long version) {
        return payments.archive(actor(token), id, version, false);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deletePaymentPermanently", summary = "Удалить ручной платёж окончательно")
    public void delete(JwtAuthenticationToken token, @PathVariable UUID id,
                       @RequestParam @PositiveOrZero long version) {
        payments.delete(actor(token), id, version);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrent_change() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Платёж изменился. Загрузите актуальную версию");
    }

    private static UUID actor(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    public enum OrderBy {
        CREATED_AT("createdAt"), UPDATED_AT("updatedAt"), AMOUNT("amount"), NUMBER("id");

        private final String field;

        OrderBy(String field) {
            this.field = field;
        }

        Sort to_sort(Sort.Direction direction) {
            return this == NUMBER ? Sort.by(direction, field) : Sort.by(direction, field, "id");
        }
    }

    public record CsvUpload(
            @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED) MultipartFile file,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Mode mode) {}

    public record CreatePayment(@NotNull @Positive Long merchantId, @Size(max = 100) String orderNumber,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @NotBlank @Size(max = 500) String description, Boolean needsReview,
                                @Size(max = 20) Set<@NotBlank @Size(max = 40) String> tags) {}

    public record UpdatePayment(@NotNull @PositiveOrZero Long version,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @NotBlank String description, @NotNull Status status,
                                @NotNull Boolean needsReview, @Size(max = 20) Set<@NotBlank @Size(max = 40) String> tags) {}
}
