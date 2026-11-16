package com.orderplatform.qa.tags;

import com.orderplatform.qa.condition.LocalEnvironmentCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags a test for the local event-driven suite and disables it automatically
 * on dev/staging, where Kafka may not be exposed to test runners.
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag(TestTags.LOCAL_ONLY)
@ExtendWith(LocalEnvironmentCondition.class)
public @interface LocalOnly {
}
