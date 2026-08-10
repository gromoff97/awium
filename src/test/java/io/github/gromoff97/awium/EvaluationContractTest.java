package io.github.gromoff97.awium;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EvaluationContractTest {

    @Test
    void satisfiedAcceptsNull() {
        Evaluation<String> evaluation = Evaluation.satisfied(null);

        assertNull(evaluation.result());
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        var nullFailure = assertThrows(NullPointerException.class,
                () -> Evaluation.unsatisfied(null));
        assertEquals("mismatch must not be null", nullFailure.getMessage());

        var blankFailure = assertThrows(IllegalArgumentException.class,
                () -> Evaluation.unsatisfied("  \n"));
        assertEquals("mismatch must not be blank", blankFailure.getMessage());
    }

    @Test
    void internalOutcomesPreserveTheirCauses() {
        var assertion = new AssertionError("failed");
        var assertionFailure = Evaluation.<String>assertionUnsatisfied(
                "assertion did not pass", assertion);
        var uncontrolled = new IllegalStateException("broken");
        var uncontrolledFailure = Evaluation.<String>uncontrolled(uncontrolled);

        assertEquals(Evaluation.Status.UNSATISFIED, assertionFailure.status());
        assertEquals("assertion did not pass", assertionFailure.mismatch());
        assertSame(assertion, assertionFailure.assertionCause());
        assertEquals(Evaluation.Status.UNCONTROLLED, uncontrolledFailure.status());
        assertSame(uncontrolled, uncontrolledFailure.uncontrolledCause());
        assertThrows(NullPointerException.class,
                () -> Evaluation.assertionUnsatisfied("failed", null));
        assertThrows(NullPointerException.class, () -> Evaluation.uncontrolled(null));
    }

    @Test
    void narrowCopiesEveryStateWithoutWideningItsPublicSurface() {
        var result = new Object();
        var assertion = new AssertionError("failed");
        var cause = new IllegalStateException("broken");

        Evaluation<Object> satisfied = Evaluation.narrow(Evaluation.satisfied(result));
        Evaluation<Object> unsatisfied = Evaluation.narrow(Evaluation.unsatisfied("no"));
        Evaluation<Object> assertionFailure = Evaluation.narrow(
                Evaluation.assertionUnsatisfied("failed", assertion));
        Evaluation<Object> uncontrolled = Evaluation.narrow(Evaluation.uncontrolled(cause));

        assertSame(result, satisfied.result());
        assertEquals("no", unsatisfied.mismatch());
        assertSame(assertion, assertionFailure.assertionCause());
        assertSame(cause, uncontrolled.uncontrolledCause());
        assertNull(Evaluation.narrow(null));
    }

    @Test
    void constructionAndStateInspectionAreNotPublic() {
        assertFalse(Arrays.stream(Evaluation.class.getDeclaredConstructors())
                .anyMatch(constructor -> isPublic(constructor.getModifiers())
                        || isProtected(constructor.getModifiers())));
        assertFalse(isPublic(Evaluation.Status.class.getModifiers()));
        assertFalse(Arrays.stream(Evaluation.class.getDeclaredMethods())
                .filter(method -> !method.getName().equals("satisfied")
                        && !method.getName().equals("unsatisfied"))
                .anyMatch(method -> isPublic(method.getModifiers())
                        || isProtected(method.getModifiers())));
    }
}
