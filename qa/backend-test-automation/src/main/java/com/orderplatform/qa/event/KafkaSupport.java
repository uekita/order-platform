package com.orderplatform.qa.event;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.TestIds;
import io.qameta.allure.Allure;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thread-safe Kafka test support: one shared idempotent producer, plus a new
 * manually-assigned consumer for each assertion. Consumers seek to the end of
 * the topic before a test publishes, so tests do not scan historical data.
 */
public final class KafkaSupport implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaSupport.class);

    private final TestConfig config;
    private final KafkaProducer<String, String> producer;
    private final AdminClient adminClient;

    public KafkaSupport(TestConfig config) {
        this.config = config;
        this.producer = new KafkaProducer<>(producerProperties(config));
        this.adminClient = AdminClient.create(adminProperties(config));
    }

    public void publish(String topic, String key, String payload) {
        long startedAt = System.nanoTime();
        try {
            var metadata = producer.send(new ProducerRecord<>(topic, key, payload))
                    .get(config.kafkaProducerTimeoutSeconds(), TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("Published Kafka message topic={} partition={} offset={} key={} ({} ms)",
                    topic, metadata.partition(), metadata.offset(), key, elapsedMs);
            attachPublishedMessage(topic, key, payload, metadata.partition(), metadata.offset());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to Kafka topic " + topic, exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Could not publish to Kafka topic " + topic
                            + ". Is the local stack running? Bootstrap: " + config.kafkaBootstrapServers(),
                    exception);
        }
    }

    public KafkaEventConsumer newConsumer(String topic, StartPosition position) {
        return new KafkaEventConsumer(config, topic, position);
    }

    public void assertTopicsAvailable() {
        try {
            Set<String> existing = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5_000))
                    .names()
                    .get(10, TimeUnit.SECONDS);
            List<String> required = List.of(
                    config.kafkaOrderEventsTopic(),
                    config.kafkaOrderEventsDlqTopic()
            );
            List<String> missing = required.stream().filter(topic -> !existing.contains(topic)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Missing Kafka topics " + missing + " on " + config.kafkaBootstrapServers()
                                + ". Start the local stack with ./scripts/local-stack-up.sh.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Kafka topics", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Kafka is not reachable at " + config.kafkaBootstrapServers()
                            + ". Start the local stack with ./scripts/local-stack-up.sh.",
                    exception);
        }
    }

    static Properties commonProperties(TestConfig config, String clientId) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBootstrapServers());
        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId);

        String protocol = config.kafkaSecurityProtocol();
        if (protocol != null && !protocol.isBlank() && !"PLAINTEXT".equalsIgnoreCase(protocol)) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);
            if (protocol.toUpperCase(Locale.ROOT).startsWith("SASL")) {
                properties.put(SaslConfigs.SASL_MECHANISM, config.kafkaSaslMechanism());
                properties.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig(config));
            }
        }
        return properties;
    }

    private static Properties producerProperties(TestConfig config) {
        Properties properties = commonProperties(config,
                config.kafkaConsumerGroupPrefix() + "-producer-" + TestIds.runId());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15_000);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return properties;
    }

    private static Properties adminProperties(TestConfig config) {
        Properties properties = commonProperties(config,
                config.kafkaConsumerGroupPrefix() + "-admin-" + TestIds.runId());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10_000);
        return properties;
    }

    private static String jaasConfig(TestConfig config) {
        String loginModule = loginModuleFor(config.kafkaSaslMechanism());
        String username = escape(config.kafkaSaslUsername());
        String password = escape(config.kafkaSaslPassword());
        return String.format("%s required username=\"%s\" password=\"%s\";",
                loginModule, username, password);
    }

    private static String loginModuleFor(String mechanism) {
        String normalized = mechanism == null ? "" : mechanism.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PLAIN" -> "org.apache.kafka.common.security.plain.PlainLoginModule";
            case "SCRAM-SHA-256", "SCRAM-SHA-512" ->
                    "org.apache.kafka.common.security.scram.ScramLoginModule";
            default -> throw new IllegalStateException(
                    "Unsupported kafka.sasl.mechanism '" + mechanism
                            + "'. Expected one of: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512");
        };
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void attachPublishedMessage(String topic,
                                               String key,
                                               String payload,
                                               int partition,
                                               long offset) {
        try {
            String content = "topic=" + topic + "\npartition=" + partition + "\noffset=" + offset
                    + "\nkey=" + key + "\n\n" + pretty(payload);
            Allure.addAttachment("Kafka published message", "application/json", content, ".json");
        } catch (RuntimeException ignored) {
            // Reporting must not affect test execution.
        }
    }

    private static String pretty(String payload) {
        try {
            return Json.pretty(Json.readTree(payload));
        } catch (RuntimeException exception) {
            return payload;
        }
    }

    @Override
    public void close() {
        producer.close(java.time.Duration.ofSeconds(5));
        adminClient.close(java.time.Duration.ofSeconds(5));
    }
}
