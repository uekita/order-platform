package com.orderplatform.qa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, validated configuration for the whole test run.
 *
 * <p>Select an environment with {@code -Denv=staging} or {@code TEST_ENV=staging}.
 * The default is {@code local} so an IDE run works without extra setup.</p>
 */
public final class TestConfig {

    private static final Logger log = LoggerFactory.getLogger(TestConfig.class);
    private static final TestConfig INSTANCE = load();

    private final TestEnvironment environment;
    private final Map<String, String> values;

    private TestConfig(TestEnvironment environment, Map<String, String> values) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.values = Map.copyOf(values);
        validate();
        log.info("Test configuration loaded: environment={} api={}{} db={} kafka={}",
                environment.id(), apiBaseUri(), apiBasePath(), redactedDbUrl(), kafkaBootstrapServers());
    }

    public static TestConfig get() {
        return INSTANCE;
    }

    public TestEnvironment environment() {
        return environment;
    }

    public boolean isLocal() {
        return environment == TestEnvironment.LOCAL;
    }

    public String apiBaseUri() {
        return raw("api.base.uri").replaceAll("/+$", "");
    }

    public String apiBasePath() {
        String path = raw("api.base.path").trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.replaceAll("/+$", "");
    }

    public int apiTimeoutMs() {
        return integer("api.timeout.ms");
    }

    public boolean apiLogRequests() {
        return bool("api.log.requests");
    }

    public boolean apiTlsInsecure() {
        return bool("api.tls.insecure");
    }

    public String apiToken() {
        return raw("api.token").trim();
    }

    public int waitTimeoutSeconds() {
        return integer("wait.timeout.seconds");
    }

    public long waitPollMillis() {
        return integer("wait.poll.millis");
    }

    public String dbUrl() {
        return raw("db.url").trim();
    }

    public String dbUsername() {
        return raw("db.username").trim();
    }

    public String dbPassword() {
        return raw("db.password");
    }

    public int dbPoolSize() {
        return integer("db.pool.size");
    }

    public long dbConnectionTimeoutMs() {
        return integer("db.connection.timeout.ms");
    }

    public String kafkaBootstrapServers() {
        return raw("kafka.bootstrap.servers").trim();
    }

    public String kafkaOrderEventsTopic() {
        return raw("kafka.topic.order.events").trim();
    }

    public String kafkaOrderEventsDlqTopic() {
        return raw("kafka.topic.order.events.dlq").trim();
    }

    public String kafkaConsumerGroupPrefix() {
        return raw("kafka.consumer.group.prefix").trim();
    }

    public long kafkaConsumerPollTimeoutMs() {
        return integer("kafka.consumer.poll.timeout.ms");
    }

    public int kafkaProducerTimeoutSeconds() {
        return integer("kafka.producer.timeout.seconds");
    }

    public int kafkaEventTimeoutSeconds() {
        return integer("kafka.event.timeout.seconds");
    }

    public String kafkaSecurityProtocol() {
        return raw("kafka.security.protocol").trim();
    }

    public String kafkaSaslMechanism() {
        return raw("kafka.sasl.mechanism").trim();
    }

    public String kafkaSaslUsername() {
        return raw("kafka.sasl.username").trim();
    }

    public String kafkaSaslPassword() {
        return raw("kafka.sasl.password");
    }

    /**
     * Safe environment snapshot for Allure and failure diagnostics. Never expose
     * passwords or tokens here.
     */
    public Map<String, String> summary() {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Environment", environment.id());
        summary.put("API", apiBaseUri() + apiBasePath());
        summary.put("Database", redactedDbUrl());
        summary.put("Kafka", kafkaBootstrapServers());
        summary.put("Kafka events topic", kafkaOrderEventsTopic());
        summary.put("Kafka DLQ topic", kafkaOrderEventsDlqTopic());
        summary.put("Wait timeout (s)", String.valueOf(waitTimeoutSeconds()));
        return summary;
    }

    public String redactedDbUrl() {
        String url = dbUrl();
        url = url.replaceAll("(?i)([?&](?:password|user)=)[^&]*", "$1***");
        url = url.replaceAll("//[^/@]+@", "//***@");
        return url;
    }

    private String raw(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing configuration key: " + key);
        }
        return value;
    }

    private int integer(String key) {
        String value = raw(key).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Configuration key " + key + " must be an integer, got: " + value);
        }
    }

    private boolean bool(String key) {
        String value = raw(key).trim();
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("Configuration key " + key + " must be true or false, got: " + value);
        }
        return Boolean.parseBoolean(value);
    }

    private void validate() {
        URI apiUri;
        try {
            apiUri = URI.create(apiBaseUri());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("api.base.uri is not a valid URI: " + apiBaseUri(), exception);
        }
        if (apiUri.getScheme() == null
                || !(apiUri.getScheme().equals("http") || apiUri.getScheme().equals("https"))
                || apiUri.getHost() == null) {
            throw new IllegalStateException("api.base.uri must be an absolute http(s) URI: " + apiBaseUri());
        }
        if (apiTimeoutMs() < 1 || apiTimeoutMs() > 120_000) {
            throw new IllegalStateException("api.timeout.ms must be between 1 and 120000");
        }
        if (waitTimeoutSeconds() < 1 || waitTimeoutSeconds() > 300) {
            throw new IllegalStateException("wait.timeout.seconds must be between 1 and 300");
        }
        if (waitPollMillis() < 10 || waitPollMillis() > 10_000) {
            throw new IllegalStateException("wait.poll.millis must be between 10 and 10000");
        }
        if (!dbUrl().startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("db.url must be a PostgreSQL JDBC URL");
        }
        if (dbUsername().isBlank()) {
            throw new IllegalStateException("db.username must not be blank");
        }
        if (dbPoolSize() < 1 || dbPoolSize() > 50) {
            throw new IllegalStateException("db.pool.size must be between 1 and 50");
        }
        if (dbConnectionTimeoutMs() < 100 || dbConnectionTimeoutMs() > 60_000) {
            throw new IllegalStateException("db.connection.timeout.ms must be between 100 and 60000");
        }
        if (kafkaBootstrapServers().isBlank()) {
            throw new IllegalStateException("kafka.bootstrap.servers must not be blank");
        }
        if (kafkaOrderEventsTopic().isBlank() || kafkaOrderEventsDlqTopic().isBlank()) {
            throw new IllegalStateException("Kafka topic names must not be blank");
        }
        if (kafkaConsumerGroupPrefix().isBlank()) {
            throw new IllegalStateException("kafka.consumer.group.prefix must not be blank");
        }
        if (kafkaConsumerPollTimeoutMs() < 50 || kafkaConsumerPollTimeoutMs() > 10_000) {
            throw new IllegalStateException("kafka.consumer.poll.timeout.ms must be between 50 and 10000");
        }
        if (kafkaProducerTimeoutSeconds() < 1 || kafkaProducerTimeoutSeconds() > 120) {
            throw new IllegalStateException("kafka.producer.timeout.seconds must be between 1 and 120");
        }
        if (kafkaEventTimeoutSeconds() < 1 || kafkaEventTimeoutSeconds() > 300) {
            throw new IllegalStateException("kafka.event.timeout.seconds must be between 1 and 300");
        }
        if (kafkaSecurityProtocol().toUpperCase(Locale.ROOT).startsWith("SASL")) {
            if (kafkaSaslMechanism().isBlank() || kafkaSaslUsername().isBlank()) {
                throw new IllegalStateException(
                        "kafka.sasl.mechanism and kafka.sasl.username are required for SASL");
            }
        }
    }

    private static TestConfig load() {
        String configuredEnvironment = System.getProperty("env");
        if (configuredEnvironment == null || configuredEnvironment.isBlank()) {
            configuredEnvironment = System.getenv("TEST_ENV");
        }
        if (configuredEnvironment == null || configuredEnvironment.isBlank()) {
            configuredEnvironment = "local";
        }
        TestEnvironment environment = TestEnvironment.from(configuredEnvironment);
        return new TestConfig(environment, ConfigLoader.load(environment));
    }
}
