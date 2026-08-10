package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InterruptGuardTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void detectsTheFlagBeforeSourceWithoutClearingIt() {
        var sourceCalls = new int[1];
        currentThread().interrupt();

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
                time, nanos -> currentThread().interrupt());

        Attempt<Object> outcome = engine.waitFor(Object::new,
                condition(value -> unsatisfied("not ready")))
                .attempt();

        assertFlagOnly(outcome, Attempt.Origin.WAITING, false, null, 2);
    }

    @Test
    void detectsTheFlagAfterSourceAndRetainsANullActual() {
        Attempt<Object> outcome = wait(() -> {
            currentThread().interrupt();
            return null;
        }, Evaluation::satisfied);

        assertFlagOnly(outcome, Attempt.Origin.SOURCE, true, null, 1);
    }

    @Test
    void detectsTheFlagAfterConditionAndRetainsTheActual() {
        var actual = new Object();

        Attempt<Object> outcome = wait(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied(value);
        });

        assertFlagOnly(outcome, Attempt.Origin.CONDITION, true, actual, 1);
    }

    @Test
    void normalCallbacksLeaveTheFlagClear() {
        Attempt<Object> outcome = wait(Object::new, Evaluation::satisfied);

        assertInstanceOf(Attempt.Satisfied.class, outcome);
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void preservesThrownInterruptionAndRestoresTheFlagAtEitherCallbackOrigin() {
        var sourceFailure = new InterruptedException("source stopped");
        Attempt<Object> source = wait(() -> {
            throw sourceFailure;
        }, Evaluation::satisfied);

        var before = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class, source);
        assertSame(sourceFailure, before.cause());
        assertTrue(currentThread().isInterrupted());
        interrupted();

        var actual = new Object();
        var conditionFailure = new InterruptedException("condition stopped");
        Attempt<Object> condition = wait(() -> actual, value -> {
            throw conditionFailure;
        });

        var after = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class, condition);
        assertSame(conditionFailure, after.cause());
        assertSame(actual, after.actual());
        assertTrue(currentThread().isInterrupted());
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
        Attempt.Uncontrolled<?> uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.class, outcome);
        if (hasActual) {
            var after = assertInstanceOf(
                    Attempt.Uncontrolled.AfterObservation.class, outcome);
            assertSame(actual, after.actual());
        } else {
            assertInstanceOf(
                    Attempt.Uncontrolled.BeforeObservation.class, outcome);
        }
        assertEquals(origin, uncontrolled.origin());
        assertEquals(number, uncontrolled.number());
        assertEquals(InterruptedException.class,
                uncontrolled.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                uncontrolled.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }
}
