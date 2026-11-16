package com.orderplatform.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.web.ApiErrorResponse;
import com.orderplatform.order.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Optional static bearer-token gate. Leave APP_API_TOKEN empty for local runs;
 * supply it in deployed environments (normally a gateway performs this check).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public ApiTokenFilter(@Value("${app.security.api-token:}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken.isBlank() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (("Bearer " + expectedToken).equals(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        String correlationId = CorrelationId.from(request.getHeader(CorrelationId.HEADER));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationId.HEADER, correlationId);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                "UNAUTHORIZED",
                "Missing or invalid bearer token",
                Map.of(),
                Instant.now(),
                correlationId
        ));
    }
}
