package ru.university.paymentgateway.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Payment;
import ru.university.paymentgateway.domain.Payment.Status;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.dto.PaymentView;
import ru.university.paymentgateway.filter.PaymentFilter;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.MerchantRepository;
import ru.university.paymentgateway.repository.PaymentRepository;

@Service
@Transactional(readOnly = true)
public class PaymentCabinetService {
    private final PaymentRepository payments;
    private final MerchantRepository merchants;
    private final AuditRepository audit;

    public PaymentCabinetService(PaymentRepository payments, MerchantRepository merchants, AuditRepository audit) {
        this.payments = payments;
        this.merchants = merchants;
        this.audit = audit;
    }

    public PageResponse<PaymentView> list(PaymentFilter filter, int page, int size, Sort sort) {
        var result = payments.findAll(filter.specification(), PageRequest.of(page, size, sort));
        return new PageResponse<>(result.map(PaymentView::from).getContent(), page, size, result.getTotalElements());
    }

    public PaymentView get(UUID id) {
        return PaymentView.from(find(id));
    }

    @Transactional
    public PaymentView create(UUID actor, long merchant_id, String order_number, BigDecimal amount, String description,
                              boolean needs_review, Set<String> tags) {
        var merchant = merchants.findById(merchant_id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден"));
        if (!merchant.active()) {
            throw conflict("Магазин отключён");
        }
        String order = order_number == null || order_number.isBlank() ? null : order_number.strip();
        var payment = new Payment(merchant, order, amount, description.strip(), needs_review, normalize_tags(tags));
        try {
            payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Номер заказа уже используется в этом магазине");
        }
        audit.record(actor, "PAYMENT_CREATED", "PAYMENT", payment.uuid(), "Создан платёж");
        return PaymentView.from(payment);
    }

    @Transactional
    public PaymentView update(UUID actor, UUID id, long version, BigDecimal amount, String description, Status status,
                              boolean needs_review, Set<String> tags) {
        Payment payment = find_editable(id, version);
        boolean amount_changed = amount.compareTo(payment.amount()) != 0;
        boolean status_changed = status != payment.status();
        if (amount_changed || status_changed) {
            require_new_manual(payment);
        }
        String updated_description = payment.description().equals(description) ? description : description.strip();
        if (!payment.description().equals(updated_description) && updated_description.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Описание должно содержать не более 500 символов");
        }
        payment.update(amount, updated_description, status, needs_review, normalize_tags(tags));
        return record_update(actor, payment, "PAYMENT_UPDATED", "Изменён платёж");
    }

    @Transactional
    public PaymentView change_status(UUID actor, UUID id, long version, Status status) {
        Payment payment = find_editable(id, version);
        if (payment.status() == status) {
            return PaymentView.from(payment);
        }
        require_new_manual(payment);
        payment.update(payment.amount(), payment.description(), status, payment.needs_review(), payment.tags());
        return record_update(actor, payment, "PAYMENT_STATUS_CHANGED", "Изменён статус платежа");
    }

    @Transactional
    public PaymentView change_tag(UUID actor, UUID id, long version, String tag, boolean add) {
        Payment payment = find_editable(id, version);
        Set<String> tags = new HashSet<>(payment.tags());
        String normalized = PaymentFilter.normalize_tag(tag);
        boolean changed = add ? tags.add(normalized) : tags.remove(normalized);
        if (!changed) {
            return PaymentView.from(payment);
        }
        if (tags.size() > 20) {
            throw conflict("У платежа может быть не более 20 тегов");
        }
        payment.update(payment.amount(), payment.description(), payment.status(), payment.needs_review(), tags);
        return record_update(actor, payment, add ? "PAYMENT_TAG_ADDED" : "PAYMENT_TAG_REMOVED",
                add ? "Добавлен тег платежа" : "Удалён тег платежа");
    }

    @Transactional
    public PaymentView archive(UUID actor, UUID id, long version, boolean archived) {
        Payment payment = find_current(id, version);
        if (payment.archived() != archived) {
            payment.archive(archived);
            return record_update(actor, payment, archived ? "PAYMENT_ARCHIVED" : "PAYMENT_RESTORED",
                    archived ? "Платёж перенесён в архив" : "Платёж восстановлен");
        }
        return PaymentView.from(payment);
    }

    @Transactional
    public void delete(UUID actor, UUID id, long version) {
        Payment payment = find_current(id, version);
        if (!payment.archived() || payment.cloudpayments()) {
            throw conflict("Окончательно удалить можно только ручной платёж из архива");
        }
        payments.delete(payment);
        payments.flush();
        audit.record(actor, "PAYMENT_DELETED", "PAYMENT", id, "Платёж удалён из архива");
    }

    private PaymentView record_update(UUID actor, Payment payment, String event, String description) {
        payments.flush();
        audit.record(actor, event, "PAYMENT", payment.uuid(), description);
        return PaymentView.from(payment);
    }

    private Payment find_editable(UUID id, long version) {
        Payment payment = find_current(id, version);
        if (payment.archived()) {
            throw conflict("Сначала восстановите платёж из архива");
        }
        return payment;
    }

    private static void require_new_manual(Payment payment) {
        if (payment.cloudpayments() || payment.status() != Status.CREATED) {
            throw conflict("Сумма и статус доступны для изменения только у нового ручного платежа");
        }
    }

    private Payment find_current(UUID id, long version) {
        Payment payment = find(id);
        if (version != payment.version()) {
            throw conflict("Платёж изменился. Загрузите актуальную версию");
        }
        return payment;
    }

    private Payment find(UUID id) {
        return payments.findByUuid(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
    }

    private static Set<String> normalize_tags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> normalized = tags.stream().map(PaymentFilter::normalize_tag).collect(Collectors.toSet());
        if (normalized.stream().anyMatch(tag -> tag.length() > 40)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тег должен быть не длиннее 40 символов");
        }
        return normalized;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
