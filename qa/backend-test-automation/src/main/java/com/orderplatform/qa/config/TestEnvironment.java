package com.orderplatform.qa.config;

import java.util.Arrays;

public enum TestEnvironment {

    LOCAL("local"),
    DEV("dev"),
    STAGING("staging");

    private final String id;

    TestEnvironment(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TestEnvironment from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(environment -> environment.id.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown test environment '" + value + "'. Expected one of: local, dev, staging"));
    }
}
