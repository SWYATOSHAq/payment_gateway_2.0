package ru.university.paymentgateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.university.paymentgateway.domain.Account;
import ru.university.paymentgateway.dto.PageResponse;
import ru.university.paymentgateway.repository.AdminRepository;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.AuthRepository;
import ru.university.paymentgateway.repository.CheckoutRepository;
import ru.university.paymentgateway.repository.MerchantRepository;
import ru.university.paymentgateway.repository.PaymentRepository;
import ru.university.paymentgateway.security.AccessTokens;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class HealthTest {
    @MockitoBean
    private CheckoutRepository payments;

    @MockitoBean
    private AuthRepository accounts;

    @MockitoBean
    private AdminRepository users;

    @MockitoBean
    private AuditRepository audit;

    @MockitoBean
    private MerchantRepository merchants;

    @MockitoBean
    private PaymentRepository cabinet_payments;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokens access_tokens;

    @Test
    void actuator_reports_application_health() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void current_user_requires_access_token() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void temporary_password_blocks_admin_access() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        var admin = new Account(user_id, "admin", "Admin", "unused", "ADMIN", true, true, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(admin));
        String authorization = "Bearer " + access_tokens.issue(user_id, session_id);

        mvc.perform(get("/api/auth/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/users").header("Authorization", authorization))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_list_users_with_correlation_id() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        UUID correlation_id = UUID.randomUUID();
        when(users.list(0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
        var admin = new Account(user_id, "admin", "Admin", "unused", "ADMIN", true, false, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(admin));

        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + access_tokens.issue(user_id, session_id))
                        .header("X-Correlation-ID", correlation_id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(header().string("X-Correlation-ID", correlation_id.toString()));
    }

    @Test
    void viewer_cannot_create_cabinet_payment() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        var viewer = new Account(user_id, "viewer", "Viewer", "unused", "VIEWER", true, false, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(viewer));

        mvc.perform(post("/api/cabinet/payments")
                        .header("Authorization", "Bearer " + access_tokens.issue(user_id, session_id)))
                .andExpect(status().isForbidden());
    }
}
