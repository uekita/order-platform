package com.orderplatform.qa.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads the environment files from the classpath and applies external overrides.
 * Keeping this explicit makes precedence obvious and avoids global Rest Assured
 * or static test state.
 */
final class ConfigLoader {

    private static final Map<String, String> ENV_VAR_TO_PROPERTY = Map.ofEntries(
            Map.entry("API_BASE_URI", "api.base.uri"),
            Map.entry("API_BASE_PATH", "api.base.path"),
            Map.entry("API_TOKEN", "api.token"),
            Map.entry("API_TIMEOUT_MS", "api.timeout.ms"),
            Map.entry("API_LOG_REQUESTS", "api.log.requests"),
            Map.entry("API_TLS_INSECURE", "api.tls.insecure"),
            Map.entry("DB_URL", "db.url"),
            Map.entry("DB_USERNAME", "db.username"),
            Map.entry("DB_PASSWORD", "db.password"),
            Map.entry("DB_POOL_SIZE", "db.pool.size"),
            Map.entry("DB_CONNECTION_TIMEOUT_MS", "db.connection.timeout.ms"),
            Map.entry("KAFKA_BOOTSTRAP_SERVERS", "kafka.bootstrap.servers"),
            Map.entry("KAFKA_TOPIC_ORDER_EVENTS", "kafka.topic.order.events"),
            Map.entry("KAFKA_TOPIC_ORDER_EVENTS_DLQ", "kafka.topic.order.events.dlq"),
            Map.entry("KAFKA_CONSUMER_GROUP_PREFIX", "kafka.consumer.group.prefix"),
            Map.entry("KAFKA_CONSUMER_POLL_TIMEOUT_MS", "kafka.consumer.poll.timeout.ms"),
            Map.entry("KAFKA_PRODUCER_TIMEOUT_SECONDS", "kafka.producer.timeout.seconds"),
            Map.entry("KAFKA_EVENT_TIMEOUT_SECONDS", "kafka.event.timeout.seconds"),
            Map.entry("KAFKA_SECURITY_PROTOCOL", "kafka.security.protocol"),
            Map.entry("KAFKA_SASL_MECHANISM", "kafka.sasl.mechanism"),
            Map.entry("KAFKA_SASL_USERNAME", "kafka.sasl.username"),
            Map.entry("KAFKA_SASL_PASSWORD", "kafka.sasl.password"),
            Map.entry("WAIT_TIMEOUT_SECONDS", "wait.timeout.seconds"),
            Map.entry("WAIT_POLL_MILLIS", "wait.poll.millis")
    );

    private ConfigLoader() {
    }

    static Map<String, String> load(TestEnvironment environment) {
        Properties properties = new Properties();
        loadClasspath(properties, "config/default.properties");
        loadClasspath(properties, "config/" + environment.id() + ".properties");

        Map<String, String> values = new LinkedHashMap<>();
        properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));

        // Environment variables are useful in CI and never require real secrets in the repo.
        ENV_VAR_TO_PROPERTY.forEach((envVar, property) -> {
            String value = System.getenv(envVar);
            if (value != null && !value.isBlank()) {
                values.put(property, value);
            }
        });

        // JVM system properties have the final say, which makes local one-off debugging easy.
        values.keySet().forEach(key -> {
            String override = System.getProperty(key);
            if (override != null) {
                values.put(key, override);
            }
        });

        return values;
    }

    private static void loadClasspath(Properties properties, String resource) {
        try (InputStream stream = ConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath configuration file: " + resource);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read classpath configuration file: " + resource, exception);
        }
    }
}
