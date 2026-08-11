package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InterruptGuardTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void detectsTheFlagAfterSourceAndRetainsANullActual() {
        Attempt<Object> outcome = wait(() -> {
            currentThread().interrupt();
            return null;
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertNull(uncontrolled.actual());
        assertEquals(Attempt.Origin.SOURCE, uncontrolled.origin());
        assertEquals(1, uncontrolled.number());
        assertEquals(InterruptedException.class,
                uncontrolled.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                uncontrolled.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void normalCallbacksLeaveTheFlagClear() {
        Attempt<Object> outcome = wait(Object::new, Evaluation::satisfied);

        assertInstanceOf(Attempt.Satisfied.class, outcome);
        assertFalse(currentThread().isInterrupted());
    }

    private static Attempt<Object> wait(
            Source<Object> source,
            CheckedFunction<Object, Evaluation<Object>> evaluator) {
        var time = new FakeTime(0);
        return new WaitEngine(new WaitConfiguration(1, 2, 0), time, time)
                .waitFor(source, new RuntimeCondition<>(evaluator,
                        () -> "test condition", null))
                .attempt();
    }
}
