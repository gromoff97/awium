package io.github.gromoff97.awium;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.*;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.Status.UNCONTROLLED;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConditionEvaluationContractTest {

    @Test
    void evaluationStatesFormASealedHierarchy() {
        assertTrue(ConditionEvaluation.class.isSealed());
    }

    @Test
    void satisfiedEvaluationContinuesWithAnotherResultType() throws Exception {
        ConditionEvaluation<String> continued = satisfied(42)
                .continueIfSatisfied(value -> satisfied("value=" + value));

        var result = assertInstanceOf(ConditionEvaluation.Satisfied.class, continued);
        assertEquals("value=42", result.result());
    }

    @Test
    void nonSatisfiedEvaluationSkipsContinuationAndKeepsDiagnostics() throws Exception {
        var assertion = new AssertionError("assertion");
        ConditionEvaluation<String> unsatisfied = ConditionEvaluation.<Integer>assertionUnsatisfied("mismatch", assertion)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });
        var cause = new IllegalStateException("broken");
        ConditionEvaluation<String> uncontrolled = ConditionEvaluation.<Integer>uncontrolled(cause)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });

        var assertionFailure = assertInstanceOf(ConditionEvaluation.AssertionUnsatisfied.class, unsatisfied);
        assertEquals(UNSATISFIED, assertionFailure.status());
        assertEquals("mismatch", assertionFailure.mismatch());
        assertSame(assertion, assertionFailure.cause());
        var uncontrolledFailure = assertInstanceOf(ConditionEvaluation.Uncontrolled.class, uncontrolled);
        assertEquals(UNCONTROLLED, uncontrolledFailure.status());
        assertSame(cause, uncontrolledFailure.cause());
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
        assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied("failed", null));
        assertThrows(NullPointerException.class, () -> uncontrolled(null));
    }

}
