package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailureTaxonomyTest {

    @Test
    void controlledFailuresRemainAssertionFailures() {
        assertTrue(AssertionError.class.isAssignableFrom(AwaitFailure.class));
        for (Class<?> type : List.of(AwaitTimeoutException.class,
                AwaitStabilizationException.class)) {
            assertTrue(AwaitFailure.class.isAssignableFrom(type));
        }
    }

    @Test
    void uncontrolledFailuresRemainRuntimeFailures() {
        assertTrue(RuntimeException.class.isAssignableFrom(
                AwaitUncontrolledException.class));
        for (Class<?> type : List.of(AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class,
                AwaitUnhandledException.class)) {
            assertTrue(AwaitUncontrolledException.class.isAssignableFrom(type));
        }
    }
}
