package ru.university.paymentgateway.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.university.paymentgateway.dto.AuthResponse;
import ru.university.paymentgateway.dto.UserProfile;
import ru.university.paymentgateway.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.login(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public UserProfile me(JwtAuthenticationToken token) {
        return auth.profile(session_id(token));
    }

    @PostMapping("/change-password")
    public AuthResponse change_password(JwtAuthenticationToken token, @Valid @RequestBody PasswordRequest request) {
        return auth.change_password(session_id(token), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(JwtAuthenticationToken token) {
        auth.logout(session_id(token));
    }

    private static UUID session_id(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getClaimAsString("sid"));
    }

    public record LoginRequest(@NotBlank @Size(max = 120) String login,
                               @NotBlank @Size(max = 72) String password) {}

    public record RefreshRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}

    public record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                  @NotBlank @Size(min = 12, max = 72) String newPassword) {}
}
