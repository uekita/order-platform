package com.orderplatform.qa.api;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.client.ContractAssertions;
import com.orderplatform.qa.api.model.OrderResponse;
import com.orderplatform.qa.base.BaseApiTest;
import com.orderplatform.qa.tags.ApiTest;
import com.orderplatform.qa.tags.ContractTest;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.util.TestDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Order Platform Backend")
@Feature("API contract")
@Story("JSON response schemas")
@Owner("backend-qa")
@RegressionTest
@ContractTest
@ApiTest
class OrderContractTest extends BaseApiTest {

    @Test
    @DisplayName("Successful order create and get responses match the published schema")
    void orderResponseMatchesSchema() {
        ApiResponse created = orders.create(TestDataFactory.newOrder());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.contentType()).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("application/json"));
        ContractAssertions.assertMatchesSchema(created, "schemas/order.schema.json");

        OrderResponse order = created.as(OrderResponse.class);
        ApiResponse fetched = orders.get(order.id());

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.contentType()).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("application/json"));
        ContractAssertions.assertMatchesSchema(fetched, "schemas/order.schema.json");
    }

    @Test
    @DisplayName("Validation and not-found error responses match the published schema")
    void errorResponseMatchesSchema() {
        ApiResponse badRequest = orders.createRaw("""
                {"customerId":"%s","amount":0.00,"currency":"CAD"}
                """.formatted(java.util.UUID.randomUUID()));

        assertThat(badRequest.status()).isEqualTo(400);
        ContractAssertions.assertMatchesSchema(badRequest, "schemas/api-error.schema.json");

        ApiResponse notFound = orders.get(java.util.UUID.randomUUID());

        assertThat(notFound.status()).isEqualTo(404);
        ContractAssertions.assertMatchesSchema(notFound, "schemas/api-error.schema.json");
    }
}
