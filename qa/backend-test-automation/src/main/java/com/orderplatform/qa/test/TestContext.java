package com.orderplatform.qa.test;

import com.orderplatform.qa.api.client.HealthClient;
import com.orderplatform.qa.api.client.OrderClient;
import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.db.Database;
import com.orderplatform.qa.db.OrderDbRepository;
import com.orderplatform.qa.event.KafkaSupport;
import com.orderplatform.qa.report.AllureEnvironmentWriter;
import com.orderplatform.qa.util.TestIds;

/**
 * Lazy, process-wide infrastructure. Database and Kafka are only initialised
 * when a test actually needs them, so API smoke tests can run when those
 * systems are not reachable from a developer laptop.
 */
public final class TestContext {

    private static final TestConfig CONFIG = TestConfig.get();
    private static volatile Database database;
    private static volatile OrderDbRepository orderDbRepository;
    private static volatile KafkaSupport kafkaSupport;

    static {
        AllureEnvironmentWriter.writeOnce(CONFIG);
        Runtime.getRuntime().addShutdownHook(new Thread(TestContext::close, "qa-test-context-shutdown"));
    }

    private TestContext() {
    }

    public static TestConfig config() {
        return CONFIG;
    }

    public static String runId() {
        return TestIds.runId();
    }

    public static OrderClient orderClient(String correlationId) {
        return new OrderClient(CONFIG, correlationId);
    }

    public static HealthClient healthClient(String correlationId) {
        return new HealthClient(CONFIG, correlationId);
    }

    public static Database database() {
        Database current = database;
        if (current == null) {
            synchronized (TestContext.class) {
                current = database;
                if (current == null) {
                    current = new Database(CONFIG);
                    database = current;
                }
            }
        }
        return current;
    }

    public static OrderDbRepository orderDbRepository() {
        OrderDbRepository current = orderDbRepository;
        if (current == null) {
            synchronized (TestContext.class) {
                current = orderDbRepository;
                if (current == null) {
                    current = new OrderDbRepository(database());
                    orderDbRepository = current;
                }
            }
        }
        return current;
    }

    public static KafkaSupport kafka() {
        KafkaSupport current = kafkaSupport;
        if (current == null) {
            synchronized (TestContext.class) {
                current = kafkaSupport;
                if (current == null) {
                    current = new KafkaSupport(CONFIG);
                    kafkaSupport = current;
                }
            }
        }
        return current;
    }

    private static void close() {
        KafkaSupport kafka = kafkaSupport;
        if (kafka != null) {
            kafka.close();
        }
        Database db = database;
        if (db != null) {
            db.close();
        }
    }
}
