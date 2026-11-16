package com.orderplatform.qa.event;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.TestIds;
import io.qameta.allure.Allure;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

public final class KafkaEventConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final long pollTimeoutMs;

    KafkaEventConsumer(TestConfig config, String topic, StartPosition position) {
        this.topic = topic;
        this.pollTimeoutMs = config.kafkaConsumerPollTimeoutMs();
        this.consumer = new KafkaConsumer<>(consumerProperties(config, position));
        List<PartitionInfo> partitionInfo = consumer.partitionsFor(topic, Duration.ofSeconds(5));
        if (partitionInfo == null || partitionInfo.isEmpty()) {
            consumer.close(Duration.ofSeconds(2));
            throw new IllegalStateException(
                    "Kafka topic '" + topic + "' does not exist or has no partitions. "
                            + "Start the local stack with ./scripts/local-stack-up.sh.");
        }

        List<TopicPartition> partitions = partitionInfo.stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .toList();
        consumer.assign(partitions);

        // The consumer is created before the test publishes. Seeking to the end
        // means assertions never scan historical records in a long-lived topic.
        if (position == StartPosition.LATEST) {
            consumer.seekToEnd(partitions);
        } else {
            consumer.seekToBeginning(partitions);
        }
        // Resolve the seek positions before the test publishes. Without this
        // call the first poll can re-evaluate the position and skip fresh records.
        partitions.forEach(consumer::position);
        log.debug("Test consumer ready topic={} assignment={} startPosition={}",
                topic, partitions, position);
    }

    public Optional<ConsumedEvent> awaitMatching(Predicate<ConsumedEvent> predicate, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long remainingMillis = Math.max(1, Duration.ofNanos(deadline - System.nanoTime()).toMillis());
            long pollMillis = Math.min(pollTimeoutMs, remainingMillis);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));
            for (ConsumerRecord<String, String> record : records) {
                ConsumedEvent event = new ConsumedEvent(
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        record.value(),
                        Instant.ofEpochMilli(record.timestamp())
                );
                if (predicate.test(event)) {
                    attach(event);
                    return Optional.of(event);
                }
            }
        }
        return Optional.empty();
    }

    public String topic() {
        return topic;
    }

    private static Properties consumerProperties(TestConfig config, StartPosition position) {
        String clientId = config.kafkaConsumerGroupPrefix() + "-consumer-" + TestIds.runId() + "-" + UUID.randomUUID();
        Properties properties = KafkaSupport.commonProperties(config, clientId);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,
                config.kafkaConsumerGroupPrefix() + "-" + TestIds.runId() + "-" + UUID.randomUUID());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                position == StartPosition.LATEST ? "latest" : "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return properties;
    }

    private void attach(ConsumedEvent event) {
        try {
            String metadata = "topic=" + event.topic()
                    + "\npartition=" + event.partition()
                    + "\noffset=" + event.offset()
                    + "\nkey=" + event.key()
                    + "\ntimestamp=" + event.timestamp();
            Allure.addAttachment("Kafka consumed message", "text/plain", metadata, ".txt");
            Allure.addAttachment("Kafka consumed payload", "application/json", pretty(event.value()), ".json");
        } catch (RuntimeException ignored) {
            // Reporting must not affect assertions.
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
        consumer.close(Duration.ofSeconds(2));
    }
}
