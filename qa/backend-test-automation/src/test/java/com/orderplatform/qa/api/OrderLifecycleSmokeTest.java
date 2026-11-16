package com.orderplatform.qa.api;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.model.CreateOrderRequest;
import com.orderplatform.qa.api.model.OrderResponse;
import com.orderplatform.qa.api.model.OrderStatus;
import com.orderplatform.qa.base.BaseApiTest;
import com.orderplatform.qa.tags.ApiTest;
import com.orderplatform.qa.tags.E2ETest;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.tags.SmokeTest;
import com.orderplatform.qa.util.TestDataFactory;
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
@Feature("Order lifecycle")
@Story("Create and retrieve an order")
@Owner("backend-qa")
@SmokeTest
@RegressionTest
@E2ETest
@ApiTest
class OrderLifecycleSmokeTest extends BaseApiTest {

    @Test
    @DisplayName("Create an order and retrieve it by id")
    @Severity(SeverityLevel.CRITICAL)
    void shouldCreateAndRetrieveOrder() {
        CreateOrderRequest request = TestDataFactory.newOrder();

        ApiResponse created = orders.create(request);

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.firstHeader("Location")).hasValueSatisfying(
                location -> assertThat(location).contains("/api/v1/orders/"));
        assertThat(created.firstHeader("X-Correlation-Id")).contains(correlationId);

        OrderResponse order = created.as(OrderResponse.class);
        assertThat(order.id()).isNotNull();
        assertThat(order.customerId()).isEqualTo(request.customerId());
        assertThat(order.amount()).isEqualByComparingTo(request.amount());
        assertThat(order.currency()).isEqualTo(request.currency());
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.correlationId()).isEqualTo(correlationId);
        assertThat(order.createdAt()).isNotNull();
        assertThat(order.updatedAt()).isNotNull();

        ApiResponse fetched = orders.get(order.id());

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.as(OrderResponse.class).id()).isEqualTo(order.id());
        assertThat(fetched.as(OrderResponse.class).correlationId()).isEqualTo(correlationId);
    }
}
