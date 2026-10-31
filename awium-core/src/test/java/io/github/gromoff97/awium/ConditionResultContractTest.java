package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.result.ConditionResult.*;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionResultContractTest {

    @Test
    void mappingChangesOnlySatisfiedValues() {
        var mapped = satisfied("abc").mapValue(String::length);
        assertEquals(3, assertInstanceOf(Satisfied.class, mapped).value());
        var cause = new IllegalStateException("not ready");
        var mismatch = ConditionResult.<String>unsatisfied("retry", cause).mapValue(value -> {
            throw new AssertionError("mismatches must not be mapped");
        });
        assertEquals("retry", assertInstanceOf(Unsatisfied.class, mismatch).mismatch());
        assertSame(cause, mismatch.cause());
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
