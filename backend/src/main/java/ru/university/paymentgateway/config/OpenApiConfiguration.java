package ru.university.paymentgateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String CHECKOUT_KEY = "checkoutKey";

    @Bean
    OpenAPI payment_gateway_api() {
        return new OpenAPI()
                .info(new Info().title("Payment Gateway").version("1.0")
                        .description("REST API учебного платёжного шлюза и кабинета КР3."))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(ACCESS_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Используйте accessToken из POST /api/auth/login."))
                        .addSecuritySchemes(CHECKOUT_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("Ключ магазина для создания checkout-платежа.")));
    }
}
