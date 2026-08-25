package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNCONTROLLED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EvaluationContractTest {

    @Test
    void satisfiedEvaluationContinuesWithAnotherResultType() throws Exception {
        Evaluation<String> continued = satisfied(42)
                .continueIfSatisfied(value -> satisfied("value=" + value));

        assertEquals("value=42", continued.result());
    }

    @Test
    void nonSatisfiedEvaluationSkipsContinuationAndKeepsDiagnostics() throws Exception {
        var assertion = new AssertionError("assertion");
        Evaluation<String> unsatisfied = Evaluation.<Integer>assertionUnsatisfied("mismatch", assertion)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });
        var cause = new IllegalStateException("broken");
        Evaluation<String> uncontrolled = Evaluation.<Integer>uncontrolled(cause)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });

        assertEquals(UNSATISFIED, unsatisfied.status());
        assertEquals("mismatch", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
        assertEquals(UNCONTROLLED, uncontrolled.status());
        assertSame(cause, uncontrolled.uncontrolledCause());
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
