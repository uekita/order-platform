package com.orderplatform.qa.base;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.report.TestDiagnosticsExtension;
import com.orderplatform.qa.test.TestContext;
import com.orderplatform.qa.util.TestIds;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;

@ExtendWith(TestDiagnosticsExtension.class)
public abstract class BaseTest {

    protected final TestConfig config = TestConfig.get();
    protected String correlationId;

    @BeforeEach
    void setUpTestContext(TestInfo testInfo) {
        correlationId = TestIds.correlationId();
        MDC.put("test.run.id", TestContext.runId());
        MDC.put("test.correlation.id", correlationId);
        MDC.put("test.name", testInfo.getDisplayName());

        Allure.parameter("environment", config.environment().id());
        Allure.parameter("runId", TestContext.runId());
        Allure.parameter("correlationId", correlationId);
        Allure.label("environment", config.environment().id());
    }

    @AfterEach
    void clearTestContext() {
        MDC.clear();
    }
}
