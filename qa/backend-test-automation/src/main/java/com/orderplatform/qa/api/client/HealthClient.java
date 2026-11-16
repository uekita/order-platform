package com.orderplatform.qa.api.client;

import com.orderplatform.qa.api.spec.ApiSpecs;
import com.orderplatform.qa.config.TestConfig;
import io.restassured.RestAssured;

public final class HealthClient {

    private final TestConfig config;
    private final String correlationId;

    public HealthClient(TestConfig config, String correlationId) {
        this.config = config;
        this.correlationId = correlationId;
    }

    public ApiResponse health() {
        return ApiResponse.from(RestAssured.given()
                .spec(ApiSpecs.base(config, correlationId))
                .get("/actuator/health"));
    }
}
