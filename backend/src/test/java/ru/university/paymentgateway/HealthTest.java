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
import ru.university.paymentgateway.repository.PaymentAnalyticsRepository;
import ru.university.paymentgateway.repository.PaymentRepository;
import ru.university.paymentgateway.repository.PaymentNoteRepository;
import ru.university.paymentgateway.repository.PaymentNoteRepository.NotePayment;
import ru.university.paymentgateway.security.AccessTokens;
import ru.university.paymentgateway.service.PaymentCsvService;

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

    @MockitoBean
    private PaymentNoteRepository notes;

    @MockitoBean
    private PaymentCsvService csv;

    @MockitoBean
    private PaymentAnalyticsRepository analytics;

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

    @Test
    void viewer_can_read_payment_notes() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        UUID payment_id = UUID.randomUUID();
        when(notes.payment(payment_id, false)).thenReturn(Optional.of(new NotePayment(1, false)));
        when(notes.list(1)).thenReturn(List.of());
        var viewer = new Account(user_id, "viewer", "Viewer", "unused", "VIEWER", true, false, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(viewer));

        mvc.perform(get("/api/cabinet/payments/{id}/notes", payment_id)
                        .header("Authorization", "Bearer " + access_tokens.issue(user_id, session_id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void bulk_requires_at_least_one_payment() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        var editor = new Account(user_id, "editor", "Editor", "unused", "EDITOR", true, false, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(editor));

        mvc.perform(post("/api/cabinet/payments/bulk")
                        .header("Authorization", "Bearer " + access_tokens.issue(user_id, session_id))
                        .contentType("application/json")
                        .content("""
                                {"operation":"ARCHIVE","items":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csv_preview_requires_a_file() throws Exception {
        UUID user_id = UUID.randomUUID();
        UUID session_id = UUID.randomUUID();
        var editor = new Account(user_id, "editor", "Editor", "unused", "EDITOR", true, false, 0, null);
        when(accounts.session_account(session_id)).thenReturn(Optional.of(editor));

        mvc.perform(post("/api/cabinet/payments/import/preview")
                        .header("Authorization", "Bearer " + access_tokens.issue(user_id, session_id))
                        .contentType("multipart/form-data;boundary=check")
                        .content("--check--\r\n")
                        .param("mode", "CREATE_ONLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analytics_requires_a_session() throws Exception {
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void exposes_openapi_document() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.components.securitySchemes.accessToken.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/payments'].post.security[0].checkoutKey").isArray())
                .andExpect(jsonPath("$.paths['/api/analytics/summary'].get.security[0].accessToken").isArray())
                .andExpect(jsonPath("$.paths['/api/cabinet/payments'].get.parameters[?(@.name == 'q')]").isNotEmpty())
                .andExpect(jsonPath("$.components.schemas.CreateCheckoutPayment.properties.merchantId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreatePayment.properties.merchantId").exists())
                .andExpect(jsonPath("$.paths['/api/cabinet/payments/import/preview'].post.requestBody.content['multipart/form-data'].schema").exists())
                .andExpect(jsonPath("$.components.schemas.CsvUpload.properties.file.format").value("binary"))
                .andExpect(jsonPath("$.components.schemas.CsvUpload.properties.mode").exists())
                .andExpect(jsonPath("$.paths['/api/cabinet/payments/import/apply'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/cabinet/payments/export'].get.responses['200'].content['text/csv'].schema.format")
                        .value("binary"));
    }

    @Test
    void swagger_ui_redirects_to_the_installed_ui() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }
}
