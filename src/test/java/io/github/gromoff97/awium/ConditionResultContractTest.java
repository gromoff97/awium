package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ConditionResult.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConditionResultContractTest {

    private static final ConditionResult.Context.Sequence SEQUENCE =
            new ConditionResult.Context.Sequence(1, 2, 1, "second stage", "business reason", null);

    @Test
    void satisfiedEvaluationContinuesWithAnotherResultType() throws Exception {
        ConditionResult<String> continued = satisfied(42)
                .continueIfSatisfied(value -> satisfied("value=" + value).withContext(SEQUENCE));

        var result = assertInstanceOf(ConditionResult.Satisfied.class, continued);
        assertEquals("value=42", result.result());
        assertSame(SEQUENCE, result.context());
    }

    @Test
    void nonSatisfiedEvaluationSkipsContinuationAndKeepsDiagnostics() throws Exception {
        var assertion = new AssertionError("assertion");
        ConditionResult<String> unsatisfied = ConditionResult.<Integer>assertionUnsatisfied("mismatch", assertion)
                .withContext(SEQUENCE)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });
        var cause = new IllegalStateException("broken");
        ConditionResult<String> uncontrolled = ConditionResult.<Integer>uncontrolled(cause)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });

        var assertionFailure = assertInstanceOf(ConditionResult.Unsatisfied.class, unsatisfied);
        assertEquals("mismatch", assertionFailure.mismatch());
        assertSame(assertion, assertionFailure.assertion());
        assertSame(SEQUENCE, assertionFailure.context());
        var uncontrolledFailure = assertInstanceOf(ConditionResult.Uncontrolled.class, uncontrolled);
        assertSame(cause, uncontrolledFailure.cause());
    }

    @Test
    void mappingSatisfiedResultKeepsItsAttemptContext() {
        ConditionResult<String> mapped = satisfied(42).withContext(SEQUENCE)
                .mapSatisfied(Object::toString);

        var result = assertInstanceOf(ConditionResult.Satisfied.class, mapped);
        assertEquals("42", result.result());
        assertSame(SEQUENCE, result.context());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> unsatisfied(null)).getMessage().contains("mismatch"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> unsatisfied("  \n")).getMessage().contains("mismatch"));
    }

    @Test
    void internalOutcomesRejectNullCauses() {
        assertEquals("mismatch must not be null", assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied(null, null)).getMessage());
        assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied("failed", null));
        assertThrows(NullPointerException.class, () -> uncontrolled(null));
    }

}
