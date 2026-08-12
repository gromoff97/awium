package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;

import io.github.gromoff97.awium.conditioning.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EvaluationContractTest {

    @Test
    void unsatisfiedValidatesMismatch() {
        assertEquals("mismatch must not be null", assertThrows(
                NullPointerException.class, () -> unsatisfied(null)).getMessage());
        assertEquals("mismatch must not be blank", assertThrows(
                IllegalArgumentException.class,
                () -> unsatisfied("  \n")).getMessage());
    }

    @Test
    void internalOutcomesRejectNullCauses() {
        assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied("failed", null));
        assertThrows(NullPointerException.class, () -> uncontrolled(null));
    }

    @Test
    void narrowCopiesEveryStateWithoutWideningItsPublicSurface() {
        var result = new Object();
        var assertion = new AssertionError("failed");
        var cause = new IllegalStateException("broken");

        Evaluation<Object> satisfied = narrow(satisfied(result));
        Evaluation<Object> unsatisfied = narrow(unsatisfied("no"));
        Evaluation<Object> assertionFailure = narrow(
                assertionUnsatisfied("failed", assertion));
        Evaluation<Object> uncontrolled = narrow(uncontrolled(cause));

        assertSame(result, satisfied.result());
        assertEquals("no", unsatisfied.mismatch());
        assertSame(assertion, assertionFailure.assertionCause());
        assertSame(cause, uncontrolled.uncontrolledCause());
        assertNull(narrow(null));
    }

}
