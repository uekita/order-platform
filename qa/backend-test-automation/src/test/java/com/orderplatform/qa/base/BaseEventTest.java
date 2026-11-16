package com.orderplatform.qa.base;

import com.orderplatform.qa.event.KafkaEventConsumer;
import com.orderplatform.qa.event.KafkaSupport;
import com.orderplatform.qa.event.StartPosition;
import com.orderplatform.qa.tags.LocalOnly;
import com.orderplatform.qa.test.TestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

@LocalOnly
public abstract class BaseEventTest extends BaseDatabaseTest {

    protected KafkaSupport kafka;

    @BeforeAll
    static void requireLocalKafka() {
        TestContext.kafka().assertTopicsAvailable();
    }

    @BeforeEach
    void createKafkaSupport() {
        kafka = TestContext.kafka();
    }

    protected KafkaEventConsumer consumer(String topic, StartPosition position) {
        return kafka.newConsumer(topic, position);
    }

    protected KafkaEventConsumer eventsConsumer() {
        return consumer(config.kafkaOrderEventsTopic(), StartPosition.LATEST);
    }

    protected KafkaEventConsumer dlqConsumer() {
        return consumer(config.kafkaOrderEventsDlqTopic(), StartPosition.LATEST);
    }

    protected Duration eventTimeout() {
        return Duration.ofSeconds(config.kafkaEventTimeoutSeconds());
    }
}
