package com.orderplatform.qa.report;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.TestIds;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds a compact, safe diagnostic block to every failure. HTTP bodies and SQL
 * results are attached by their respective layers.
 */
public class TestDiagnosticsExtension implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("Test", context.getDisplayName());
        diagnostics.put("Test id", context.getUniqueId());
        diagnostics.put("Thread", Thread.currentThread().getName());
        diagnostics.put("Run id", TestIds.runId());
        diagnostics.put("Correlation id", MDC.get("test.correlation.id"));
        diagnostics.putAll(TestConfig.get().summary());
        try {
            Allure.addAttachment("Failure diagnostics", "text/plain", diagnostics.toString(), ".txt");
        } catch (RuntimeException ignored) {
            // Diagnostics must never mask the real failure.
        }
    }
}
