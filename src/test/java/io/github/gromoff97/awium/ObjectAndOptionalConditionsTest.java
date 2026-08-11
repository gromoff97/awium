package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

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
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);

        assertSatisfied(evaluate(equalTo(expected), equalActual),
                equalActual);
        assertUnsatisfied(evaluate(notEqualTo(expected), equalActual),
                "value was equal");
        assertUnsatisfied(evaluate(equalTo(expected), differentActual),
                "value was not equal");
        assertSatisfied(evaluate(notEqualTo(expected), differentActual),
                differentActual);
        assertSatisfied(evaluate(equalTo(null), null), null);
        assertSatisfied(evaluate(notEqualTo(expected), null), null);
        Evaluation<?> arrays = evaluate(equalTo(
                new int[]{1, 2}), new int[]{1, 2});
        assertEquals(Evaluation.Status.SATISFIED, arrays.status());
        assertEquals(int[].class, arrays.result().getClass());
        assertNull(arrays.mismatch());
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent()
            throws Exception {
        Optional<String> empty = Optional.empty();
        Optional<String> present = Optional.of("value");

        assertEquals("optional to remain present",
                RuntimeCondition.<String>present(ConditionProvider.present)
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
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);
        var equal = hasValueEqualTo(expected);
        var notEqual = hasValueNotEqualTo(expected);

        assertUnsatisfied(equal.evaluate(null), "optional was null");
        assertUnsatisfied(notEqual.evaluate(null), "optional was null");
        assertUnsatisfied(equal.evaluate(Optional.empty()), "optional was empty");
        assertUnsatisfied(notEqual.evaluate(Optional.empty()), "optional was empty");
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
        var expected = new RightOperand();
        var actual = new LeftOperand(expected);

        assertSatisfied(hasValueEqualTo((Object) expected)
                .evaluate(Optional.of(actual)), actual);
        assertEquals(1, actual.comparisons);
        assertEquals(0, expected.comparisons);

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
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(result, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertUnsatisfied(
            Evaluation<?> evaluation, String mismatch) {
        assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(mismatch, evaluation.mismatch());
    }

    private record EqualValue(int value) {
    }

    @SuppressWarnings("overrides")
    private static final class LeftOperand {
        private final Object expected;
        private int comparisons;

        private LeftOperand(Object expected) {
            this.expected = expected;
        }

        @Override
        public boolean equals(Object other) {
            comparisons++;
            return other == expected;
        }
    }

    @SuppressWarnings("overrides")
    private static final class RightOperand {
        private int comparisons;

        @Override
        public boolean equals(Object other) {
            comparisons++;
            return false;
        }
    }
}
