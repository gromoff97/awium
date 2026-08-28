package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalConditionsTest {

    @Test
    void nullabilityConditionsAreExactPositiveAndNegativePairs()
            throws Exception {
        var actual = new Object();

        assertSatisfied(evaluate(isNull, null), null);
        assertUnsatisfied(evaluate(isNull, actual));
        assertSatisfied(evaluate(isNotNull, actual), actual);
        assertUnsatisfied(evaluate(isNotNull, null));
    }

    @Test
    void equalityConditionsAreComplementsAndReturnTheActualSnapshot()
            throws Exception {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";

        assertSatisfied(evaluate(equalTo(expected), equalActual),
                equalActual);
        assertUnsatisfied(evaluate(notEqualTo(expected), equalActual));
        assertUnsatisfied(evaluate(equalTo(expected), differentActual));
        assertSatisfied(evaluate(notEqualTo(expected), differentActual),
                differentActual);
        assertSatisfied(evaluate(equalTo(null), null), null);
        Evaluation<?> arrays = evaluate(equalTo(
                new int[]{1, 2}), new int[]{1, 2});
        assertEquals(SATISFIED, arrays.status());
        assertEquals(int[].class, arrays.result().getClass());
        assertNull(arrays.mismatch());
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent()
            throws Exception {
        Optional<String> empty = Optional.empty();
        Optional<String> presentValue = Optional.of("value");

        assertTrue(!description(present).isBlank());
        assertUnsatisfied(evaluatePresent(null));
        assertUnsatisfied(evaluate(absent, null));
        assertUnsatisfied(evaluatePresent(empty));
        assertSatisfied(evaluate(absent, empty), null);
        assertSatisfied(evaluatePresent(presentValue), "value");
        assertUnsatisfied(evaluate(absent, presentValue));
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue()
            throws Exception {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";
        var equal = hasValue(expected);
        var notEqual = doesNotHaveValue(expected);

        assertUnsatisfied(evaluate(equal, null));
        assertUnsatisfied(evaluate(equal, Optional.empty()));
        assertSatisfied(evaluate(equal, Optional.of(equalActual)), equalActual);
        assertUnsatisfied(evaluate(notEqual, Optional.of(equalActual)));
        assertUnsatisfied(evaluate(equal, Optional.of(differentActual)));
        assertSatisfied(evaluate(notEqual, Optional.of(differentActual)),
                differentActual);
    }

    @Test
    void optionalValueEqualityUsesActualFirstAndArrayContent() throws Exception {
        var expected = new Directional(false);
        var actual = new Directional(true);

        assertSatisfied(evaluate(hasValue((Object) expected),
                Optional.of(actual)), actual);
        assertEquals(1, actual.equalsCalls);

        int[] actualArray = {1, 2};
        assertSame(actualArray, evaluate(hasValue(
                new int[]{1, 2}), Optional.of(actualArray)).result());
    }

    @Test
    void optionalValueFactoriesRejectNullOperandsImmediately() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> hasValue((Object) null)).getMessage().contains("expected"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> doesNotHaveValue(null)).getMessage()
                .contains("unexpected"));
    }

    private static Evaluation<?> evaluatePresent(Optional<?> actual)
            throws Exception {
        return evaluate(present, actual);
    }

    private static void assertSatisfied(Evaluation<?> evaluation, Object result) {
        assertEquals(SATISFIED, evaluation.status());
        assertSame(result, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertTrue(!evaluation.mismatch().isBlank());
    }
}
