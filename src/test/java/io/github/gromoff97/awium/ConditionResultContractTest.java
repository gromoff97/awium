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
    void satisfiedEvaluationContinuesWithAnotherResultType() {
        ConditionResult<String> continued = satisfied(42)
                .continueIfSatisfied(value -> satisfied("value=" + value));

        var result = assertInstanceOf(ConditionResult.Satisfied.class, continued);
        assertEquals("value=42", result.result());
    }

    @Test
    void nonSatisfiedEvaluationSkipsContinuationAndKeepsDiagnostics() {
        var assertion = new AssertionError("assertion");
        ConditionResult<String> unsatisfied = ConditionResult.<Integer>assertionUnsatisfied("mismatch", assertion).continueIfSatisfied(value -> {
            throw new AssertionError("continuation must not run");
        });

        var assertionFailure = assertInstanceOf(ConditionResult.Unsatisfied.class, unsatisfied);
        assertEquals("mismatch", assertionFailure.mismatch());
        assertSame(assertion, assertionFailure.assertion());
    }

    @Test
    void mappingSatisfiedResultChangesItsValue() {
        ConditionResult<String> mapped = satisfied(42)
                .mapSatisfied(Object::toString);

        var result = assertInstanceOf(ConditionResult.Satisfied.class, mapped);
        assertEquals("42", result.result());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> unsatisfied(null)).getMessage().contains("mismatch"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> unsatisfied("  \n")).getMessage().contains("mismatch"));
    }

    @Test
    void assertionUnsatisfiedValidatesItsMismatchAndCause() {
        assertEquals("mismatch must not be null", assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied(null, null)).getMessage());
        assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied("failed", null));
    }

}
