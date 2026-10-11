package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectAndOptionalConditionsTest {

    @Test
    void nullabilityConditionsAreExactPositiveAndNegativePairs() {
        var actual = new Object();

        assertNull(result(evaluate(isNull, null)));
        assertUnsatisfied(evaluate(isNull, actual));
        assertSame(actual, result(evaluate(isNotNull, actual)));
        assertUnsatisfied(evaluate(isNotNull, null));
    }

    @Test
    void equalityConditionsAreComplementsAndReturnTheActualSnapshot() {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";

        assertSame(equalActual, result(evaluate(equalTo(expected), equalActual)));
        assertUnsatisfied(evaluate(notEqualTo(expected), equalActual));
        assertUnsatisfied(evaluate(equalTo(expected), differentActual));
        assertSame(differentActual, result(evaluate(notEqualTo(expected), differentActual)));
        assertNull(result(evaluate(equalTo(null), null)));
        ConditionResult<?> arrays = evaluate(equalTo(
                new int[]{1, 2}), new int[]{1, 2});
        assertEquals(Satisfied.class, arrays.getClass());
        assertEquals(int[].class, result(arrays).getClass());
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent() {
        Optional<String> empty = Optional.empty();
        Optional<String> presentValue = Optional.of("value");

        assertFalse(description(present).isBlank());
        assertUnsatisfied(evaluate(present, null));
        assertUnsatisfied(evaluate(absent, null));
        assertUnsatisfied(evaluate(present, empty));
        assertNull(result(evaluate(absent, empty)));
        assertSame("value", result(evaluate(present, presentValue)));
        assertUnsatisfied(evaluate(absent, presentValue));
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue() {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";
        Condition<Optional<String>, String> equal = hasValue(equalTo(expected));
        Condition<Optional<String>, String> notEqual = hasValue(notEqualTo(expected));

        assertUnsatisfied(evaluate(equal, null));
        assertUnsatisfied(evaluate(equal, Optional.empty()));
        assertSame(equalActual, result(evaluate(equal, Optional.of(equalActual))));
        assertUnsatisfied(evaluate(notEqual, Optional.of(equalActual)));
        assertUnsatisfied(evaluate(equal, Optional.of(differentActual)));
        assertSame(differentActual, result(evaluate(notEqual, Optional.of(differentActual))));
    }

    @Test
    void optionalValueEqualityUsesActualFirstAndArrayContent() {
        var expected = new Directional(false);
        var actual = new Directional(true);

        assertSame(actual, result(evaluate(hasValue(equalTo((Object) expected)),
                Optional.of(actual))));
        assertEquals(1, actual.equalsCalls);

        int[] actualArray = {1, 2};
        assertSame(actualArray, result(evaluate(hasValue(equalTo(
                new int[]{1, 2})), Optional.of(actualArray))));
    }

    @Test
    void optionalValueFactoriesRejectNullConditionsImmediately() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> hasValue((Condition<Object, Object>) null)).getMessage().contains("condition"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> hasValue((Condition.PreservingCondition<Object>) null)).getMessage().contains("condition"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> hasValue((Condition.ExpectedCondition<Object>) null)).getMessage().contains("condition"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> hasValue((Condition.NarrowingCondition<Object>) null)).getMessage().contains("condition"));
    }
}
