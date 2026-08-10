package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InterruptGuardTest {

    private final InterruptGuard guard = new InterruptGuard();

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void detectsTheFlagBeforeSourceWithoutClearingIt() {
        Thread.currentThread().interrupt();

        ObservationOutcome<Object> outcome = guard.checkWaiting(1);

        assertFlagOnly(outcome, ObservationOutcome.Origin.WAITING, false, null, 1);
    }

    @Test
    void detectsTheFlagAfterParkWithoutClearingIt() {
        Thread.currentThread().interrupt();

        ObservationOutcome<Object> outcome = guard.checkWaiting(12);

        assertFlagOnly(outcome, ObservationOutcome.Origin.WAITING,
                false, null, 12);
    }

    @Test
    void detectsTheFlagAfterSourceAndRetainsANullActual() {
        Thread.currentThread().interrupt();

        ObservationOutcome<Object> outcome = guard.checkSource(2, null);

        assertFlagOnly(outcome, ObservationOutcome.Origin.SOURCE, true, null, 2);
    }

    @Test
    void detectsTheFlagAfterConditionAndRetainsTheActual() {
        var actual = new Object();
        Thread.currentThread().interrupt();

        ObservationOutcome<Object> outcome = guard.checkCondition(3, actual);

        assertFlagOnly(outcome, ObservationOutcome.Origin.CONDITION,
                true, actual, 3);
    }

    @Test
    void returnsNullWhenNoFlagIsSet() {
        assertNull(guard.checkWaiting(1));
        assertNull(guard.checkSource(1, new Object()));
        assertNull(guard.checkCondition(1, new Object()));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void preservesThrownInterruptionAndRestoresTheFlagAtEitherCallbackOrigin() {
        var sourceFailure = new InterruptedException("source stopped");
        ObservationOutcome<Object> source = guard.fromThrown(
                ObservationOutcome.Origin.SOURCE, sourceFailure, 4);

        assertSame(sourceFailure, source.cause());
        assertFalse(source.hasActual());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();

        var actual = new Object();
        var conditionFailure = new InterruptedException("condition stopped");
        ObservationOutcome<Object> condition = guard.fromThrown(
                ObservationOutcome.Origin.CONDITION, conditionFailure, 5, actual);

        assertSame(conditionFailure, condition.cause());
        assertTrue(condition.hasActual());
        assertSame(actual, condition.actual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static void assertFlagOnly(
            ObservationOutcome<?> outcome,
            ObservationOutcome.Origin origin,
            boolean hasActual,
            Object actual,
            long attempt) {
        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(origin, outcome.origin());
        assertEquals(hasActual, outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(attempt, outcome.attempt());
        assertEquals(InterruptedException.class, outcome.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                outcome.cause().getMessage());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
