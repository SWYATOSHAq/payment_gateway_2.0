package ru.university.paymentgateway.service;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.dto.PaymentNoteView;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.PaymentNoteRepository;
import ru.university.paymentgateway.repository.PaymentNoteRepository.NotePayment;

@Service
public class PaymentNoteService {
    private final PaymentNoteRepository notes;
    private final AuditRepository audit;

    public PaymentNoteService(PaymentNoteRepository notes, AuditRepository audit) {
        this.notes = notes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PaymentNoteView> list(UUID payment) {
        return notes.list(find_payment(payment, false).id());
    }

    @Transactional
    public PaymentNoteView create(UUID payment, UUID actor, String text, boolean pinned) {
        long number = editable_payment(payment);
        UUID id = notes.create(number, actor, text.strip(), pinned);
        audit.record(actor, "NOTE_CREATED", "PAYMENT_NOTE", id, "Добавлена заметка к платежу");
        return find_note(number, id);
    }

    @Transactional
    public PaymentNoteView update(UUID payment, UUID id, UUID actor, boolean admin, String text, boolean pinned) {
        long number = editable_payment(payment);
        require_owner(number, id, actor, admin);
        notes.update(number, id, text.strip(), pinned);
        audit.record(actor, "NOTE_UPDATED", "PAYMENT_NOTE", id, "Изменена заметка к платежу");
        return find_note(number, id);
    }

    @Transactional
    public void delete(UUID payment, UUID id, UUID actor, boolean admin) {
        long number = editable_payment(payment);
        require_owner(number, id, actor, admin);
        notes.delete(number, id);
        audit.record(actor, "NOTE_DELETED", "PAYMENT_NOTE", id, "Удалена заметка к платежу");
    }

    private long editable_payment(UUID id) {
        NotePayment payment = find_payment(id, true);
        if (payment.archived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала восстановите платёж из архива");
        }
        return payment.id();
    }

    private NotePayment find_payment(UUID id, boolean lock) {
        return notes.payment(id, lock)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден"));
    }

    private PaymentNoteView find_note(long payment, UUID id) {
        return notes.find(payment, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заметка не найдена"));
    }

    private void require_owner(long payment, UUID id, UUID actor, boolean admin) {
        PaymentNoteView note = find_note(payment, id);
        if (!admin && !note.authorId().equals(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Изменить заметку может её автор или администратор");
        }
    }
}
