package ru.university.paymentgateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID id = parse_id(request.getHeader(HEADER));
        response.setHeader(HEADER, id.toString());
        try (var ignored = MDC.putCloseable(MDC_KEY, id.toString())) {
            chain.doFilter(request, response);
        }
    }

    public static UUID current_id() {
        return parse_id(MDC.get(MDC_KEY));
    }

    private static UUID parse_id(String value) {
        if (value != null && value.length() == 36) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Replace malformed client IDs with a valid request ID.
            }
        }
        return UUID.randomUUID();
    }
}
