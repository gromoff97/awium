package io.github.gromoff97.awium.result;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionResultTest {

    @Test
    void mapsSuccessfulValues() {
        assertEquals(satisfied(3), satisfied("abc").mapValue(String::length));
    }

    @Test
    void mappingSkipsMismatchesAndPreservesTheirDiagnostics() {
        var cause = new IllegalStateException("not ready");
        var mismatch = ConditionResult.<String>unsatisfied("retry", cause).mapValue(value -> {
            throw new AssertionError("mismatches must not be mapped");
        });
        assertEquals(new Unsatisfied<>("retry", cause), mismatch);
    }

    @Test
    void unsatisfiedAcceptsOptionalDiagnostics() {
        var plain = unsatisfied();
        assertEquals(new Unsatisfied<>(null, null), plain);
        assertEquals(plain, unsatisfied(null));
        assertEquals(plain, unsatisfied(null, null));
        var cause = new IllegalStateException("details");
        var detailed = unsatisfied("not ready", cause);
        assertEquals("not ready", detailed.mismatch());
        assertSame(cause, detailed.cause());
        assertSame(cause, unsatisfied(null, cause).cause());
        assertEquals(new Unsatisfied<>("failed", null), unsatisfied("failed", null));
    }
}
