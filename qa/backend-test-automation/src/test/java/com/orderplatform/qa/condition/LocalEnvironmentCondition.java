package com.orderplatform.qa.condition;

import com.orderplatform.qa.config.TestConfig;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@code localonly} is a hard capability gate, not just a label. Selecting the
 * tag in an ad-hoc staging run reports the tests as skipped instead of failing
 * because a Kafka broker is not exposed there.
 */
public class LocalEnvironmentCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        TestConfig config = TestConfig.get();
        if (config.isLocal()) {
            return ConditionEvaluationResult.enabled("Local event-driven environment");
        }
        return ConditionEvaluationResult.disabled(
                "localonly test skipped because the active environment is '" + config.environment().id() + "'");
    }
}
