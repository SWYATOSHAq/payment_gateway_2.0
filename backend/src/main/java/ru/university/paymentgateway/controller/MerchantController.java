package ru.university.paymentgateway.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.MerchantView;
import ru.university.paymentgateway.service.MerchantService;

@RestController
@RequestMapping("/api/cabinet/merchants")
public class MerchantController {
    private final MerchantService merchants;

    public MerchantController(MerchantService merchants) {
        this.merchants = merchants;
    }

    @GetMapping
    public List<MerchantView> list() {
        return merchants.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantView create(JwtAuthenticationToken token, @Valid @RequestBody CreateMerchant request) {
        return merchants.create(UUID.fromString(token.getToken().getSubject()), request.name(), request.website());
    }

    @PutMapping("/{id}")
    public MerchantView update(JwtAuthenticationToken token, @PathVariable @Positive long id,
                               @Valid @RequestBody UpdateMerchant request) {
        return merchants.update(UUID.fromString(token.getToken().getSubject()), id,
                request.name(), request.website(), request.active());
    }

    public record CreateMerchant(@NotBlank @Size(max = 100) String name,
                                 @NotBlank @Size(max = 255) String website) {}

    public record UpdateMerchant(@NotBlank @Size(max = 100) String name,
                                 @NotBlank @Size(max = 255) String website, @NotNull Boolean active) {}
}
