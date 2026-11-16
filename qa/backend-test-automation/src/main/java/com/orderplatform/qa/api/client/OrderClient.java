package com.orderplatform.qa.api.client;

import com.orderplatform.qa.api.model.CreateOrderRequest;
import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.api.spec.ApiSpecs;
import io.restassured.RestAssured;

import java.util.UUID;

public final class OrderClient {

    private final TestConfig config;
    private final String correlationId;

    public OrderClient(TestConfig config, String correlationId) {
        this.config = config;
        this.correlationId = correlationId;
    }

    public OrderClient withoutCorrelationId() {
        return new OrderClient(config, null);
    }

    public ApiResponse create(CreateOrderRequest request) {
        return ApiResponse.from(RestAssured.given()
                .spec(ApiSpecs.api(config, correlationId))
                .body(request)
                .post("/orders"));
    }

    public ApiResponse createRaw(String jsonBody) {
        return ApiResponse.from(RestAssured.given()
                .spec(ApiSpecs.api(config, correlationId))
                .body(jsonBody)
                .post("/orders"));
    }

    public ApiResponse createWithContentType(String body, String contentType) {
        return ApiResponse.from(RestAssured.given()
                .spec(ApiSpecs.api(config, correlationId))
                .contentType(contentType)
                .body(body)
                .post("/orders"));
    }

    public ApiResponse get(UUID orderId) {
        return ApiResponse.from(RestAssured.given()
                .spec(ApiSpecs.api(config, correlationId))
                .pathParam("orderId", orderId)
                .get("/orders/{orderId}"));
    }
}
