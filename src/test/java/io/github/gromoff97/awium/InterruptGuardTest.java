package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InterruptGuardTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void detectsTheFlagBeforeSourceWithoutClearingIt() {
        var sourceCalls = new int[1];
        Thread.currentThread().interrupt();

        Attempt<Object> outcome = wait(() -> {
            sourceCalls[0]++;
            return new Object();
        }, Evaluation::satisfied);

        assertFlagOnly(outcome, Attempt.Origin.WAITING, false, null, 1);
        assertEquals(0, sourceCalls[0]);
    }

    @Test
    void detectsTheFlagAfterParkWithoutClearingIt() {
        var time = new FakeTime(0);
        WaitEngine engine = new WaitEngine(new WaitConfiguration(1, 2, 0),
                time, nanos -> Thread.currentThread().interrupt());

        Attempt<Object> outcome = engine.waitFor(Object::new,
                condition(value -> Evaluation.unsatisfied("not ready")))
                .attempt();

        assertFlagOnly(outcome, Attempt.Origin.WAITING, false, null, 2);
    }

    @Test
    void detectsTheFlagAfterSourceAndRetainsANullActual() {
        Attempt<Object> outcome = wait(() -> {
            Thread.currentThread().interrupt();
            return null;
        }, Evaluation::satisfied);

        assertFlagOnly(outcome, Attempt.Origin.SOURCE, true, null, 1);
    }

    @Test
    void detectsTheFlagAfterConditionAndRetainsTheActual() {
        var actual = new Object();

        Attempt<Object> outcome = wait(() -> actual, value -> {
            Thread.currentThread().interrupt();
            return Evaluation.satisfied(value);
        });

        assertFlagOnly(outcome, Attempt.Origin.CONDITION, true, actual, 1);
    }

    @Test
    void normalCallbacksLeaveTheFlagClear() {
        Attempt<Object> outcome = wait(Object::new, Evaluation::satisfied);

        assertEquals(Attempt.Status.SATISFIED, outcome.status());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void preservesThrownInterruptionAndRestoresTheFlagAtEitherCallbackOrigin() {
        var sourceFailure = new InterruptedException("source stopped");
        Attempt<Object> source = wait(() -> {
            throw sourceFailure;
        }, Evaluation::satisfied);

        assertSame(sourceFailure, source.cause());
        assertFalse(source.hasActual());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();

        var actual = new Object();
        var conditionFailure = new InterruptedException("condition stopped");
        Attempt<Object> condition = wait(() -> actual, value -> {
            throw conditionFailure;
        });

        assertSame(conditionFailure, condition.cause());
        assertTrue(condition.hasActual());
        assertSame(actual, condition.actual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static Attempt<Object> wait(
            Source<Object> source,
            CheckedFunction<Object, Evaluation<Object>> evaluator) {
        var time = new FakeTime(0);
        return new WaitEngine(new WaitConfiguration(1, 2, 0), time, time)
                .waitFor(source, condition(evaluator)).attempt();
    }

    private static RuntimeCondition<Object, Object> condition(
            CheckedFunction<Object, Evaluation<Object>> evaluator) {
        return new RuntimeCondition<>(evaluator, () -> "test condition", null);
    }

    private static void assertFlagOnly(Attempt<?> outcome,
            Attempt.Origin origin, boolean hasActual, Object actual,
            long number) {
        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(origin, outcome.origin());
        assertEquals(hasActual, outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(number, outcome.number());
        assertEquals(InterruptedException.class, outcome.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                outcome.cause().getMessage());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
