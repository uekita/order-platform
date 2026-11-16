package com.orderplatform.qa.db;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.model.CreateOrderRequest;
import com.orderplatform.qa.api.model.OrderResponse;
import com.orderplatform.qa.base.BaseDatabaseTest;
import com.orderplatform.qa.tags.ApiTest;
import com.orderplatform.qa.tags.DatabaseTest;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.util.TestDataFactory;
import com.orderplatform.qa.util.Waits;
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
@Feature("Database consistency")
@Story("API writes are visible in PostgreSQL")
@Owner("backend-qa")
@RegressionTest
@DatabaseTest
@ApiTest
class OrderPersistenceRegressionTest extends BaseDatabaseTest {

    @Test
    @DisplayName("A created order is persisted with its correlation id")
    @Severity(SeverityLevel.CRITICAL)
    void shouldPersistCreatedOrder() {
        CreateOrderRequest request = TestDataFactory.newOrder();

        ApiResponse response = orders.create(request);

        assertThat(response.status()).isEqualTo(201);
        OrderResponse order = response.as(OrderResponse.class);

        OrderDbRepository.OrderRow row = DatabaseAssertions.awaitOrderPersisted(config, db, order.id());
        assertThat(row.customerId()).isEqualTo(request.customerId());
        assertThat(row.amount()).isEqualByComparingTo(request.amount());
        assertThat(row.currency()).isEqualTo(request.currency());
        assertThat(row.status()).isEqualTo("CREATED");
        assertThat(row.correlationId()).isEqualTo(correlationId);

        Waits.await(config)
                .alias("exactly one order for correlation id " + correlationId)
                .untilAsserted(() -> assertThat(db.countOrdersByCorrelationId(correlationId)).isEqualTo(1));
    }
}
