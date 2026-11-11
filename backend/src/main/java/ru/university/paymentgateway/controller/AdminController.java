package ru.university.paymentgateway.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.AdminUser;
import ru.university.paymentgateway.dto.AdminUser.Role;
import ru.university.paymentgateway.dto.AuditEntry;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.service.AdminService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService users;

    public AdminController(AdminService users) {
        this.users = users;
    }

    @GetMapping("/users")
    public PageResponse<AdminUser> users(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(page, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUser create(JwtAuthenticationToken token, @Valid @RequestBody CreateUser request) {
        return users.create(actor(token), request.login(), request.displayName(), request.role(), request.temporaryPassword());
    }

    @PutMapping("/users/{id}")
    public AdminUser update(JwtAuthenticationToken token, @PathVariable UUID id,
                            @Valid @RequestBody UpdateUser request) {
        return users.update(actor(token), id, request.login(), request.displayName(), request.role(), request.active());
    }

    @PostMapping("/users/{id}/reset-password")
    public AdminUser reset_password(JwtAuthenticationToken token, @PathVariable UUID id,
                                    @Valid @RequestBody ResetPassword request) {
        return users.reset_password(actor(token), id, request.temporaryPassword());
    }

    @GetMapping("/audit")
    public PageResponse<AuditEntry> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                         @RequestParam(defaultValue = "") @Size(max = 120) String q,
                                         @RequestParam(defaultValue = "DESC") AuditSort sort) {
        return users.list_audit(page, size, q, sort == AuditSort.ASC);
    }

    private static UUID actor(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    public enum AuditSort {
        ASC, DESC
    }

    public record CreateUser(@NotBlank @Size(max = 120) String login,
                             @NotBlank @Size(max = 120) String displayName,
                             @NotNull Role role,
                             @NotBlank @Size(min = 12, max = 72) String temporaryPassword) {}

    public record UpdateUser(@NotBlank @Size(max = 120) String login,
                             @NotBlank @Size(max = 120) String displayName,
                             @NotNull Role role, @NotNull Boolean active) {}

    public record ResetPassword(@NotBlank @Size(min = 12, max = 72) String temporaryPassword) {}
}
