package com.orderplatform.qa.api;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.base.BaseApiTest;
import com.orderplatform.qa.tags.ApiTest;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.tags.SmokeTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Order Platform Backend")
@Feature("Service availability")
@Story("Health endpoint")
@Owner("backend-qa")
@SmokeTest
@RegressionTest
@ApiTest
class HealthSmokeTest extends BaseApiTest {

    @Test
    @DisplayName("Health endpoint reports UP")
    @Severity(SeverityLevel.BLOCKER)
    void healthEndpointReportsUp() {
        ApiResponse response = health.health();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("status").asText()).isEqualTo("UP");
        assertThat(response.contentType()).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("application/json"));
    }
}
