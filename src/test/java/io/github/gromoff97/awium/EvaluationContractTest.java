package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EvaluationContractTest {

    @Test
    void satisfiedAcceptsNull() {
        Evaluation<String> evaluation = satisfied(null);

        assertNull(evaluation.result());
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        var nullFailure = assertThrows(NullPointerException.class,
                () -> unsatisfied(null));
        assertEquals("mismatch must not be null", nullFailure.getMessage());

        var blankFailure = assertThrows(IllegalArgumentException.class,
                () -> unsatisfied("  \n"));
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

    @Test
    void constructionIsClosedAndStateInspectionIsPublic() throws Exception {
        assertFalse(Arrays.stream(Evaluation.class.getDeclaredConstructors())
                .anyMatch(constructor -> isPublic(constructor.getModifiers())
                        || isProtected(constructor.getModifiers())));
        assertTrue(isPublic(Evaluation.Status.class.getModifiers()));
        for (String accessor : new String[] {"status", "result", "mismatch",
                "assertionCause", "uncontrolledCause"}) {
            assertTrue(isPublic(Evaluation.class.getDeclaredMethod(accessor)
                    .getModifiers()), accessor);
        }
    }
}
