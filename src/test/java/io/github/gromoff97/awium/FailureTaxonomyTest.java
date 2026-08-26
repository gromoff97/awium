package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailureTaxonomyTest {

    @Test
    void controlledFailuresRemainAssertionFailures() {
        assertTrue(AssertionError.class.isAssignableFrom(AwaitFailure.class));
        for (Class<?> type : List.of(AwaitTimeoutException.class,
                AwaitPersistenceException.class)) {
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
