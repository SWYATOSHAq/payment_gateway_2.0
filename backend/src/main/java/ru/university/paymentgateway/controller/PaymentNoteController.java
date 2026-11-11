package ru.university.paymentgateway.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.PaymentNoteView;
import ru.university.paymentgateway.service.PaymentNoteService;

@RestController
@RequestMapping("/api/cabinet/payments/{paymentId}/notes")
public class PaymentNoteController {
    private final PaymentNoteService notes;

    public PaymentNoteController(PaymentNoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<PaymentNoteView> list(@PathVariable UUID paymentId) {
        return notes.list(paymentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentNoteView create(@PathVariable UUID paymentId, JwtAuthenticationToken token,
                                  @Valid @RequestBody NoteRequest request) {
        return notes.create(paymentId, actor(token), request.text(), request.pinned());
    }

    @PutMapping("/{id}")
    public PaymentNoteView update(@PathVariable UUID paymentId, @PathVariable UUID id, JwtAuthenticationToken token,
                                  @Valid @RequestBody NoteRequest request) {
        return notes.update(paymentId, id, actor(token), admin(token), request.text(), request.pinned());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID paymentId, @PathVariable UUID id, JwtAuthenticationToken token) {
        notes.delete(paymentId, id, actor(token), admin(token));
    }

    private static UUID actor(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    private static boolean admin(JwtAuthenticationToken token) {
        return token.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String text, boolean pinned) {}
}
