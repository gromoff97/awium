package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionResult.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionResultContractTest {

    @Test
    void mappingSatisfiedResultChangesItsValue() {
        ConditionResult<String> mapped = satisfied(42)
                .mapSatisfied(Object::toString);

        var result = assertInstanceOf(ConditionResult.Satisfied.class, mapped);
        assertEquals("42", result.result());
    }

    @Test
    void mappingUnsatisfiedSkipsTheCallbackAndKeepsItsCause() {
        var cause = new IllegalStateException("cause");
        ConditionResult<String> mapped = ConditionResult.<Integer>unsatisfied("mismatch", cause).mapSatisfied(value -> {
            throw new AssertionError("mapping must not run");
        });
        var failure = assertInstanceOf(ConditionResult.Unsatisfied.class, mapped);
        assertEquals("mismatch", failure.mismatch());
        assertSame(cause, failure.cause());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> unsatisfied(null)).getMessage().contains("mismatch"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> unsatisfied("  \n")).getMessage().contains("mismatch"));
    }

    @Test
    void unsatisfiedWithCauseValidatesItsArguments() {
        assertEquals("mismatch must not be null", assertThrows(NullPointerException.class,
                () -> unsatisfied(null, null)).getMessage());
        assertThrows(NullPointerException.class,
                () -> unsatisfied("failed", null));
    }

}
