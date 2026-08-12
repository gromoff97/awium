package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EvaluationContractTest {

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
