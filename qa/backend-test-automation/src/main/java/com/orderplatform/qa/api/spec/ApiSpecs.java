package com.orderplatform.qa.api.spec;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.Json;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Builds a fresh, immutable-by-convention specification for every request.
 * Global Rest Assured state is never mutated, so tests are safe in parallel.
 */
public final class ApiSpecs {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private ApiSpecs() {
    }

    public static RequestSpecification api(TestConfig config, String correlationId) {
        return baseBuilder(config, correlationId)
                .setBasePath(config.apiBasePath())
                .build();
    }

    public static RequestSpecification base(TestConfig config, String correlationId) {
        return baseBuilder(config, correlationId).build();
    }

    private static RequestSpecBuilder baseBuilder(TestConfig config, String correlationId) {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(config.apiBaseUri())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(restAssuredConfig(config))
                .addFilter(new HttpExchangeFilter(config.apiLogRequests()));

        if (correlationId != null && !correlationId.isBlank()) {
            builder.addHeader(CORRELATION_ID_HEADER, correlationId);
        }

        if (!config.apiToken().isBlank()) {
            builder.addHeader("Authorization", "Bearer " + config.apiToken());
        }
        if (config.apiTlsInsecure()) {
            builder.setRelaxedHTTPSValidation();
        }
        return builder;
    }

    private static RestAssuredConfig restAssuredConfig(TestConfig config) {
        ObjectMapperConfig objectMapperConfig = ObjectMapperConfig.objectMapperConfig()
                .jackson2ObjectMapperFactory((type, charset) -> Json.mapper());

        HttpClientConfig httpClientConfig = HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", config.apiTimeoutMs())
                .setParam("http.socket.timeout", config.apiTimeoutMs());

        return RestAssuredConfig.config()
                .objectMapperConfig(objectMapperConfig)
                .httpClient(httpClientConfig);
    }
}
