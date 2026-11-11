package ru.university.paymentgateway.service;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Payment.Status;
import ru.university.paymentgateway.dto.CsvImportReport;
import ru.university.paymentgateway.dto.CsvImportReport.Mode;
import ru.university.paymentgateway.dto.CsvImportReport.RowError;
import ru.university.paymentgateway.filter.PaymentFilter;
import ru.university.paymentgateway.repository.MerchantRepository;
import ru.university.paymentgateway.repository.PaymentRepository;
import ru.university.paymentgateway.service.PaymentCsvCodec.PaymentRow;

@Service
public class PaymentCsvService {
    private final PaymentCsvCodec codec;
    private final PaymentRepository payments;
    private final MerchantRepository merchants;
    private final PaymentCabinetService cabinet;
    private final EntityManager entity_manager;

    PaymentCsvService(PaymentCsvCodec codec, PaymentRepository payments, MerchantRepository merchants,
                       PaymentCabinetService cabinet, EntityManager entity_manager) {
        this.codec = codec;
        this.payments = payments;
        this.merchants = merchants;
        this.cabinet = cabinet;
        this.entity_manager = entity_manager;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(PaymentFilter filter, Sort sort, OutputStream output) throws IOException {
        var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        var printer = codec.printer(writer);
        var specification = filter.specification();
        var request = PageRequest.of(0, 100, sort);
        while (true) {
            var page = payments.findAll(specification, request);
            for (var payment : page) {
                codec.write(printer, payment);
            }
            entity_manager.clear();
            if (!page.hasNext()) {
                break;
            }
            request = request.next();
        }
        printer.flush();
    }

    @Transactional(readOnly = true)
    public CsvImportReport preview(MultipartFile file, Mode mode) {
        ImportPlan plan = prepare(file, mode);
        return plan.report(0, 0);
    }

    @Transactional
    public CsvImportReport apply(UUID actor, MultipartFile file, Mode mode) {
        ImportPlan plan = prepare(file, mode);
        if (!plan.errors().isEmpty()) {
            return plan.report(0, 0);
        }
        int created = 0;
        int updated = 0;
        for (var entry : plan.entries()) {
            PaymentRow row = entry.row();
            if (entry.existing_id() == null) {
                var payment = cabinet.create(actor, row.merchantId(), row.orderNumber(), row.amount(),
                        row.description(), row.needsReview(), row.tags());
                if (row.status() != Status.CREATED) {
                    cabinet.change_status(actor, payment.id(), payment.version(), row.status());
                }
                created++;
            } else {
                cabinet.update(actor, entry.existing_id(), entry.version(), row.amount(), row.description(),
                        row.status(), row.needsReview(), row.tags());
                updated++;
            }
        }
        return plan.report(created, updated);
    }

    private ImportPlan prepare(MultipartFile file, Mode mode) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите CSV-файл");
        }
        if (file.getSize() > 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Размер CSV не должен превышать 1 МБ");
        }
        PaymentCsvCodec.ParsedFile parsed;
        try {
            parsed = codec.read(file.getInputStream());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось открыть CSV");
        }
        var errors = new ArrayList<>(parsed.errors());
        var entries = new ArrayList<ImportEntry>();
        var keys = new HashSet<OrderKey>();
        for (var row : parsed.rows()) {
            try {
                if (!keys.add(new OrderKey(row.merchantId(), row.orderNumber()))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер заказа повторяется в файле для этого магазина");
                }
                entries.add(check_row(row, mode));
            } catch (ResponseStatusException exception) {
                errors.add(new RowError(row.row(), exception.getReason()));
            }
        }
        errors.sort(Comparator.comparingLong(RowError::row));
        return new ImportPlan(entries, errors);
    }

    private ImportEntry check_row(PaymentRow row, Mode mode) {
        var merchant = merchants.findById(row.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден"));
        if (!merchant.active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Магазин отключён");
        }
        var existing = payments.find_order(row.merchantId(), row.orderNumber());
        if (existing.isEmpty()) {
            return new ImportEntry(row, null, 0);
        }
        if (mode == Mode.CREATE_ONLY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ уже существует");
        }
        var payment = existing.orElseThrow();
        PaymentCabinetService.validate_edit(payment, row.amount(), row.status());
        return new ImportEntry(row, payment.uuid(), payment.version());
    }

    private record OrderKey(long merchant_id, String order_number) {}
    private record ImportEntry(PaymentRow row, UUID existing_id, long version) {}
    private record ImportPlan(List<ImportEntry> entries, List<RowError> errors) {
        CsvImportReport report(int created, int updated) {
            return new CsvImportReport(entries.size(), errors.size(), created, updated, errors);
        }
    }
}
