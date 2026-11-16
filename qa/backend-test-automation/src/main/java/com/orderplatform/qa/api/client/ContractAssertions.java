package com.orderplatform.qa.api.client;

import io.qameta.allure.Allure;
import io.restassured.module.jsv.JsonSchemaValidator;
import org.hamcrest.MatcherAssert;

public final class ContractAssertions {

    private ContractAssertions() {
    }

    public static void assertMatchesSchema(ApiResponse response, String classpathSchema) {
        assertMatchesSchema(response.body(), classpathSchema);
    }

    public static void assertMatchesSchema(String json, String classpathSchema) {
        try {
            MatcherAssert.assertThat(json, JsonSchemaValidator.matchesJsonSchemaInClasspath(classpathSchema));
            Allure.addAttachment("JSON schema validated", "text/plain", classpathSchema, ".txt");
        } catch (AssertionError error) {
            Allure.addAttachment("Invalid JSON payload", "application/json", json, ".json");
            Allure.addAttachment("Expected JSON schema", "text/plain", classpathSchema, ".txt");
            throw error;
        }
    }
}
