package com.orderplatform.qa.api;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.model.ApiErrorResponse;
import com.orderplatform.qa.api.model.OrderResponse;
import com.orderplatform.qa.base.BaseApiTest;
import com.orderplatform.qa.tags.ApiTest;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.util.TestDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Order Platform Backend")
@Feature("Order API validation")
@Story("Reject invalid requests with a stable error contract")
@Owner("backend-qa")
@RegressionTest
@ApiTest
class OrderValidationRegressionTest extends BaseApiTest {

    @Test
    @DisplayName("Negative amount is rejected")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectNegativeAmount() {
        String body = """
                {"customerId":"%s","amount":-5.00,"currency":"CAD"}
                """.formatted(UUID.randomUUID());

        ApiResponse response = orders.createRaw(body);

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.as(ApiErrorResponse.class).details()).containsKey("amount");
    }

    @Test
    @DisplayName("Missing customerId is rejected")
    void shouldRejectMissingCustomerId() {
        ApiResponse response = orders.createRaw("""
                {"amount":10.00,"currency":"CAD"}
                """);

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.as(ApiErrorResponse.class).details()).containsKey("customerId");
    }

    @Test
    @DisplayName("Lower-case currency is rejected")
    void shouldRejectInvalidCurrency() {
        ApiResponse response = orders.createRaw("""
                {"customerId":"%s","amount":10.00,"currency":"cad"}
                """.formatted(UUID.randomUUID()));

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.as(ApiErrorResponse.class).details()).containsKey("currency");
    }

    @Test
    @DisplayName("Malformed JSON is rejected with a specific error code")
    void shouldRejectMalformedJson() {
        ApiResponse response = orders.createRaw("{\"customerId\":");

        assertError(response, 400, "MALFORMED_JSON");
    }

    @Test
    @DisplayName("Amount with more than two fractional digits is rejected")
    void shouldRejectAmountWithExcessiveFractionDigits() {
        String body = """
                {"customerId":"%s","amount":10.999,"currency":"CAD"}
                """.formatted(UUID.randomUUID());

        ApiResponse response = orders.createRaw(body);

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.as(ApiErrorResponse.class).details()).containsKey("amount");
    }

    @Test
    @DisplayName("Non-JSON content type is rejected with 415")
    void shouldRejectUnsupportedMediaType() {
        ApiResponse response = orders.createWithContentType("customerId=123", "text/plain");

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("Missing X-Correlation-Id header causes the server to generate a UUID correlation id")
    void shouldGenerateCorrelationIdWhenHeaderIsOmitted() {
        ApiResponse response = orders.withoutCorrelationId().create(TestDataFactory.newOrder());

        assertThat(response.status()).isEqualTo(201);
        String generated = response.firstHeader("X-Correlation-Id").orElseThrow();
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(response.as(OrderResponse.class).correlationId()).isEqualTo(generated);
    }

    @Test
    @DisplayName("Unknown order returns 404")
    @Severity(SeverityLevel.NORMAL)
    void shouldReturnNotFoundForUnknownOrder() {
        ApiResponse response = orders.get(UUID.randomUUID());

        assertError(response, 404, "NOT_FOUND");
    }

    private void assertError(ApiResponse response, int expectedStatus, String expectedCode) {
        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(response.firstHeader("X-Correlation-Id")).contains(correlationId);

        ApiErrorResponse error = response.as(ApiErrorResponse.class);
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.message()).isNotBlank();
        assertThat(error.timestamp()).isNotNull();
        assertThat(error.correlationId()).isEqualTo(correlationId);
    }
}
