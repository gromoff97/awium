package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalConditionsTest {

    @Test
    void nullabilityConditionsAreExactPositiveAndNegativePairs()
            throws Exception {
        var actual = new Object();

        assertSatisfied(isNull.evaluate(null), null);
        assertUnsatisfied(isNull.evaluate(actual),
                "value was not null");
        assertSatisfied(evaluate(isNotNull, actual), actual);
        assertUnsatisfied(evaluate(isNotNull, null),
                "value was null");
    }

    @Test
    void equalityConditionsAreComplementsAndReturnTheActualSnapshot()
            throws Exception {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";

        assertSatisfied(evaluate(equalTo(expected), equalActual),
                equalActual);
        assertUnsatisfied(evaluate(notEqualTo(expected), equalActual),
                "value was equal");
        assertUnsatisfied(evaluate(equalTo(expected), differentActual),
                "value was not equal");
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
        Optional<String> present = Optional.of("value");

        assertEquals("optional to remain present",
                RuntimeCondition.<String>present(OptionalConditionProvider.present)
                        .description().get());
        assertUnsatisfied(evaluatePresent(null), "optional was null");
        assertUnsatisfied(absent.evaluate(null), "optional was null");
        assertUnsatisfied(evaluatePresent(empty), "optional was empty");
        assertSatisfied(absent.evaluate(empty), null);
        assertSatisfied(evaluatePresent(present), "value");
        assertUnsatisfied(absent.evaluate(present),
                "optional was present");
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue()
            throws Exception {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";
        var equal = hasValueEqualTo(expected);
        var notEqual = hasValueNotEqualTo(expected);

        assertUnsatisfied(equal.evaluate(null), "optional was null");
        assertUnsatisfied(equal.evaluate(Optional.empty()), "optional was empty");
        assertSatisfied(equal.evaluate(Optional.of(equalActual)), equalActual);
        assertUnsatisfied(notEqual.evaluate(Optional.of(equalActual)),
                "optional value was equal");
        assertUnsatisfied(equal.evaluate(Optional.of(differentActual)),
                "optional value was not equal");
        assertSatisfied(notEqual.evaluate(Optional.of(differentActual)),
                differentActual);
    }

    @Test
    void optionalValueEqualityUsesActualFirstAndArrayContent() throws Exception {
        var expected = new Directional(false);
        var actual = new Directional(true);

        assertSatisfied(hasValueEqualTo((Object) expected)
                .evaluate(Optional.of(actual)), actual);
        assertEquals(1, actual.equalsCalls);

        int[] actualArray = {1, 2};
        assertSame(actualArray, hasValueEqualTo(
                new int[]{1, 2}).evaluate(Optional.of(actualArray)).result());
    }

    @Test
    void optionalValueFactoriesRejectNullOperandsImmediately() {
        assertEquals("expected must not be null", assertThrows(
                NullPointerException.class,
                () -> hasValueEqualTo(null)).getMessage());
        assertEquals("unexpected must not be null", assertThrows(
                NullPointerException.class,
                () -> hasValueNotEqualTo(null)).getMessage());
    }

    @Test
    void builtInObjectAndOptionalConditionsExposeExactDescriptions() {
        assertEquals("value to be null", isNull.description());
        assertEquals("value to be non-null",
                preserving(isNotNull)
                        .description().get());
        assertEquals("value equal to expected",
                preserving(equalTo("expected"))
                        .description().get());
        assertEquals("value not equal to unexpected",
                preserving(
                        notEqualTo("unexpected"))
                        .description().get());
        assertEquals("optional to be absent",
                absent.description());
        assertEquals("optional value equal to expected",
                hasValueEqualTo("expected").description());
        assertEquals("optional value not equal to unexpected",
                hasValueNotEqualTo("unexpected").description());
    }

    private static Evaluation<Object> evaluate(
            PreservingCondition<Object> condition, Object actual) throws Exception {
        return preserving(condition).evaluate(actual);
    }

    private static <T> Evaluation<T> evaluatePresent(Optional<T> actual)
            throws Exception {
        return RuntimeCondition.<T>present(present).evaluate(actual);
    }

    private static void assertSatisfied(Evaluation<?> evaluation, Object result) {
        assertEquals(SATISFIED, evaluation.status());
        assertSame(result, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertUnsatisfied(
            Evaluation<?> evaluation, String mismatch) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(mismatch, evaluation.mismatch());
    }
}
