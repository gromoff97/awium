package io.github.gromoff97.awium.execution;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProbeFailureTest {
    @Test
    void keepsTheLocationAndOriginalCause() {
        var cause = new IOException("offline");
        var failure = locatedFailure("receipt", cause);

        assertEquals("receipt", failure.location());
        assertSame(cause, failure.getCause());
    }

    @Test
    void addsAnOuterLocationWithoutChangingTheNestedFailure() {
        var cause = new IOException("offline");
        var nested = locatedFailure("receipt", cause);
        var outer = locatedFailure("payments[2]", nested);

        assertEquals("payments[2] → receipt", outer.location());
        assertSame(cause, outer.getCause());
        assertEquals("receipt", nested.location());
        assertSame(cause, nested.getCause());
    }

    @Test
    void aLocatedProbeReturnsTheUnchangedSuccessfulResult() throws Throwable {
        var expected = satisfied(42);
        var execution = new ConditionExecution<String, Integer>(() -> actual -> expected);

        assertSame(expected, execution.probeAt("ready", "receipt"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aLocatedProbeIdentifiesFactoryAndEvaluatorFailures(boolean factoryFails) {
        var cause = new IOException("offline");
        var execution = new ConditionExecution<String, Integer>(() -> {
            if (factoryFails) throw cause;
            return actual -> { throw cause; };
        });

        var failure = assertThrows(ProbeFailure.class, () -> execution.probeAt("ready", "receipt"));
        assertEquals("receipt", failure.location());
        assertSame(cause, failure.getCause());
    }

    @Test
    void nestedProbesKeepTheOriginalFailureAndFullLocation() {
        var cause = new IOException("offline");
        var inner = new ConditionExecution<String, Integer>(() -> actual -> { throw cause; });
        var outer = new ConditionExecution<String, Integer>(() -> actual -> inner.probeAt(actual, "receipt"));

        var failure = assertThrows(ProbeFailure.class, () -> outer.probeAt("ready", "payments[2]"));
        assertEquals("payments[2] → receipt", failure.location());
        assertSame(cause, failure.getCause());
    }
}
