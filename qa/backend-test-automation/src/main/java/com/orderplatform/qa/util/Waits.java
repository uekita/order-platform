package com.orderplatform.qa.util;

import com.orderplatform.qa.config.TestConfig;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;

/**
 * All eventual-consistency assertions flow through this helper. No arbitrary
 * sleeps belong in tests; timeouts are environment-configurable.
 */
public final class Waits {

    private Waits() {
    }

    public static ConditionFactory await(TestConfig config) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(config.waitTimeoutSeconds()))
                .pollInterval(Duration.ofMillis(config.waitPollMillis()))
                .pollInSameThread();
    }

    public static ConditionFactory await(TestConfig config, Duration atMost) {
        return Awaitility.await()
                .atMost(atMost)
                .pollInterval(Duration.ofMillis(config.waitPollMillis()))
                .pollInSameThread();
    }
}
