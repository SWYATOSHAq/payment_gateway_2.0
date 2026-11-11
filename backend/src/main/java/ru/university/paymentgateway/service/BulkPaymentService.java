package ru.university.paymentgateway.service;

import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.dto.BulkPaymentRequest;
import ru.university.paymentgateway.dto.BulkPaymentResult;
import ru.university.paymentgateway.dto.BulkPaymentResult.Failure;
import ru.university.paymentgateway.dto.BulkPaymentResult.Success;
import ru.university.paymentgateway.dto.PaymentView;
import ru.university.paymentgateway.filter.PaymentFilter;

@Service
public class BulkPaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BulkPaymentService.class);
    private final PaymentCabinetService payments;

    public BulkPaymentService(PaymentCabinetService payments) {
        this.payments = payments;
    }

    public BulkPaymentResult execute(UUID actor, BulkPaymentRequest request) {
        validate(request);
        var successes = new ArrayList<Success>();
        var errors = new ArrayList<Failure>();
        // Each call into PaymentCabinetService commits or rolls back its own transaction.
        for (var item : request.items()) {
            try {
                PaymentView payment = apply(actor, request, item);
                successes.add(new Success(payment.id(), payment.version()));
            } catch (ResponseStatusException exception) {
                errors.add(new Failure(item.id(), exception.getStatusCode().value(), exception.getReason()));
            } catch (OptimisticLockingFailureException exception) {
                errors.add(new Failure(item.id(), 409, "Платёж изменился. Загрузите актуальную версию"));
            } catch (DataAccessException exception) {
                LOGGER.error("Bulk operation {} failed for payment {} ({})",
                        request.operation(), item.id(), exception.getClass().getSimpleName());
                errors.add(new Failure(item.id(), 500, "Не удалось сохранить изменение"));
            }
        }
        return new BulkPaymentResult(successes, errors);
    }

    private PaymentView apply(UUID actor, BulkPaymentRequest request, BulkPaymentRequest.Item item) {
        return switch (request.operation()) {
            case ARCHIVE -> payments.archive(actor, item.id(), item.version(), true);
            case RESTORE -> payments.archive(actor, item.id(), item.version(), false);
            case CHANGE_STATUS -> payments.change_status(actor, item.id(), item.version(), request.status());
            case ADD_TAG -> payments.change_tag(actor, item.id(), item.version(), request.tag(), true);
            case REMOVE_TAG -> payments.change_tag(actor, item.id(), item.version(), request.tag(), false);
        };
    }

    private static void validate(BulkPaymentRequest request) {
        if (request.items().stream().map(BulkPaymentRequest.Item::id).distinct().count() != request.items().size()) {
            throw bad_request("Платёж указан несколько раз");
        }
        switch (request.operation()) {
            case CHANGE_STATUS -> {
                if (request.status() == null || request.tag() != null) {
                    throw bad_request("Для смены статуса укажите только status");
                }
            }
            case ADD_TAG, REMOVE_TAG -> {
                if (request.status() != null || request.tag() == null || request.tag().isBlank()
                        || PaymentFilter.normalize_tag(request.tag()).length() > 40) {
                    throw bad_request("Для операции с тегом укажите только tag длиной до 40 символов");
                }
            }
            case ARCHIVE, RESTORE -> {
                if (request.status() != null || request.tag() != null) {
                    throw bad_request("Для архива и восстановления достаточно списка платежей");
                }
            }
        }
    }

    private static ResponseStatusException bad_request(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
