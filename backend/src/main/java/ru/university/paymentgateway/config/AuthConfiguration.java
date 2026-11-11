package ru.university.paymentgateway.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import ru.university.paymentgateway.repository.AuthRepository;
import ru.university.paymentgateway.security.AccessTokens;
import ru.university.paymentgateway.service.AuthService;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class AuthConfiguration {
    @Bean
    SecurityFilterChain account_security(HttpSecurity http, AuthRepository accounts) throws Exception {
        return http.securityMatcher("/api/auth/**", "/api/admin/**", "/api/cabinet/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/cabinet/**").hasAnyRole("VIEWER", "EDITOR", "ADMIN")
                        .requestMatchers("/api/cabinet/merchants", "/api/cabinet/merchants/**",
                                "/api/cabinet/payments/*/permanent").hasRole("ADMIN")
                        .requestMatchers("/api/cabinet/**").hasAnyRole("EDITOR", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    var account = accounts.session_account(UUID.fromString(token.getClaimAsString("sid")))
                            .filter(user -> user.id().toString().equals(token.getSubject()))
                            .orElseThrow(() -> new InvalidBearerTokenException("Сессия истекла"));
                    String role = account.must_change_password() ? "PASSWORD_CHANGE" : account.role();
                    return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                })))
                .build();
    }

    @Bean
    SecretKey access_key(@Value("${gateway.auth.jwt-secret}") String secret,
                         @Value("${gateway.auth.admin-login}") String admin_login) {
        if (secret.isBlank() && !admin_login.isBlank()) {
            throw new IllegalStateException("GATEWAY_JWT_SECRET is required for configured accounts");
        }
        byte[] bytes = secret.isBlank() ? AccessTokens.random_bytes() : Base64.getDecoder().decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("GATEWAY_JWT_SECRET must contain at least 32 bytes encoded as Base64");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwt_encoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwt_decoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(AccessTokens.ISSUER)));
        return decoder;
    }

    @Bean
    PasswordEncoder password_encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ApplicationRunner bootstrap_admin(AuthRepository accounts, PasswordEncoder passwords,
                                      @Value("${gateway.auth.admin-login}") String login,
                                      @Value("${gateway.auth.admin-password}") String password) {
        return args -> {
            if (login.isBlank() && password.isBlank()) {
                return;
            }
            if (login.isBlank() || login.strip().length() > 120) {
                throw new IllegalStateException("Set a valid GATEWAY_ADMIN_LOGIN for the initial administrator");
            }
            AuthService.validate_new_password(password);
            accounts.bootstrap_admin(AuthService.normalize_login(login), "Администратор", passwords.encode(password));
        };
    }
}
