package com.orderplatform.qa.base;

import com.orderplatform.qa.api.client.HealthClient;
import com.orderplatform.qa.api.client.OrderClient;
import com.orderplatform.qa.test.TestContext;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseApiTest extends BaseTest {

    protected OrderClient orders;
    protected HealthClient health;

    @BeforeEach
    void createClients() {
        orders = TestContext.orderClient(correlationId);
        health = TestContext.healthClient(correlationId);
    }
}
