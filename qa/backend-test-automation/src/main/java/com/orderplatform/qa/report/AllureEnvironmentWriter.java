package com.orderplatform.qa.report;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.TestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes Allure's environment, executor and category metadata before the first
 * test. This is what makes a CI artifact self-explanatory months later.
 */
public final class AllureEnvironmentWriter {

    private static final Logger log = LoggerFactory.getLogger(AllureEnvironmentWriter.class);
    private static final AtomicBoolean WRITTEN = new AtomicBoolean();

    private AllureEnvironmentWriter() {
    }

    public static void writeOnce(TestConfig config) {
        if (!WRITTEN.compareAndSet(false, true)) {
            return;
        }
        Path resultsDirectory = Path.of(
                System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(resultsDirectory);
            writeEnvironment(resultsDirectory, config);
            writeExecutor(resultsDirectory);
            writeCategories(resultsDirectory);
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not write Allure environment metadata to {}", resultsDirectory, exception);
        }
    }

    private static void writeEnvironment(Path directory, TestConfig config) throws IOException {
        StringBuilder properties = new StringBuilder();
        Map<String, String> values = new LinkedHashMap<>(config.summary());
        values.put("Run ID", TestIds.runId());
        values.put("Java", System.getProperty("java.version", "unknown"));
        values.put("OS", System.getProperty("os.name", "unknown") + " "
                + System.getProperty("os.arch", "unknown"));
        values.put("Git commit", valueOrDefault(System.getenv("GITHUB_SHA"), "local"));
        values.forEach((key, value) -> properties.append(key).append('=').append(value).append('\n'));
        Files.writeString(directory.resolve("environment.properties"), properties.toString(), StandardCharsets.UTF_8);
    }

    private static void writeExecutor(Path directory) throws IOException {
        String serverUrl = System.getenv("GITHUB_SERVER_URL");
        String repository = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");
        boolean githubActions = serverUrl != null && repository != null && runId != null;

        Map<String, String> executor = new LinkedHashMap<>();
        executor.put("name", githubActions ? "GitHub Actions" : "local");
        executor.put("type", githubActions ? "github" : "local");
        executor.put("reportName", "Order Platform - Backend Test Automation");
        if (githubActions) {
            executor.put("buildName", System.getenv().getOrDefault("GITHUB_WORKFLOW", "workflow") + " #"
                    + System.getenv().getOrDefault("GITHUB_RUN_NUMBER", runId));
            executor.put("buildUrl", serverUrl + "/" + repository + "/actions/runs/" + runId);
            executor.put("buildOrder", System.getenv().getOrDefault("GITHUB_RUN_NUMBER", "0"));
        }
        Files.writeString(directory.resolve("executor.json"), Json.pretty(executor), StandardCharsets.UTF_8);
    }

    private static void writeCategories(Path directory) throws IOException {
        String categories = """
                [
                  {
                    "name": "Product defects",
                    "matchedStatuses": ["failed"],
                    "messageRegex": ".*(AssertionError|expected|but was).*"
                  },
                  {
                    "name": "Infrastructure / environment",
                    "matchedStatuses": ["broken"],
                    "messageRegex": ".*(Connection|Timeout|Kafka|database|SQL|unreachable).*"
                  },
                  {
                    "name": "Environment-gated (localonly)",
                    "matchedStatuses": ["skipped"]
                  }
                ]
                """;
        Files.writeString(directory.resolve("categories.json"), categories, StandardCharsets.UTF_8);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
