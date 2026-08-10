package io.github.gromoff97.awium;

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

        assertSatisfied(ConditionProvider.isNull.evaluate(null), null);
        assertUnsatisfied(ConditionProvider.isNull.evaluate(actual),
                "value was not null");
        assertSatisfied(evaluate(ConditionProvider.isNotNull, actual), actual);
        assertUnsatisfied(evaluate(ConditionProvider.isNotNull, null),
                "value was null");
    }

    @Test
    void equalityConditionsAreComplementsAndReturnTheActualSnapshot()
            throws Exception {
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);

        assertSatisfied(evaluate(ConditionProvider.equalTo(expected), equalActual),
                equalActual);
        assertUnsatisfied(evaluate(ConditionProvider.notEqualTo(expected), equalActual),
                "value was equal");
        assertUnsatisfied(evaluate(ConditionProvider.equalTo(expected), differentActual),
                "value was not equal");
        assertSatisfied(evaluate(ConditionProvider.notEqualTo(expected), differentActual),
                differentActual);
        assertSatisfied(evaluate(ConditionProvider.equalTo(null), null), null);
        assertSatisfied(evaluate(ConditionProvider.notEqualTo(expected), null), null);
        assertSatisfiedType(evaluate(ConditionProvider.equalTo(
                new int[]{1, 2}), new int[]{1, 2}), int[].class);
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
        assertUnsatisfied(ConditionProvider.absent.evaluate(null), "optional was null");
        assertUnsatisfied(evaluatePresent(empty), "optional was empty");
        assertSatisfied(ConditionProvider.absent.evaluate(empty), null);
        assertSatisfied(evaluatePresent(present), "value");
        assertUnsatisfied(ConditionProvider.absent.evaluate(present),
                "optional was present");
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue()
            throws Exception {
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);
        var equal = ConditionProvider.hasValueEqualTo(expected);
        var notEqual = ConditionProvider.hasValueNotEqualTo(expected);

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

        assertSatisfied(ConditionProvider.hasValueEqualTo((Object) expected)
                .evaluate(Optional.of(actual)), actual);
        assertEquals(1, actual.comparisons);
        assertEquals(0, expected.comparisons);

        int[] actualArray = {1, 2};
        assertSame(actualArray, ConditionProvider.hasValueEqualTo(
                new int[]{1, 2}).evaluate(Optional.of(actualArray)).result());
    }

    @Test
    void optionalValueFactoriesRejectNullOperandsImmediately() {
        assertEquals("expected must not be null", assertThrows(
                NullPointerException.class,
                () -> ConditionProvider.hasValueEqualTo(null)).getMessage());
        assertEquals("unexpected must not be null", assertThrows(
                NullPointerException.class,
                () -> ConditionProvider.hasValueNotEqualTo(null)).getMessage());
    }

    @Test
    void builtInObjectAndOptionalConditionsExposeExactDescriptions() {
        assertEquals("value to be null", ConditionProvider.isNull.description());
        assertEquals("value to be non-null",
                RuntimeCondition.preserving(ConditionProvider.isNotNull)
                        .description().get());
        assertEquals("value equal to expected",
                RuntimeCondition.preserving(ConditionProvider.equalTo("expected"))
                        .description().get());
        assertEquals("value not equal to unexpected",
                RuntimeCondition.preserving(
                        ConditionProvider.notEqualTo("unexpected"))
                        .description().get());
        assertEquals("optional to be absent",
                ConditionProvider.absent.description());
        assertEquals("optional value equal to expected",
                ConditionProvider.hasValueEqualTo("expected").description());
        assertEquals("optional value not equal to unexpected",
                ConditionProvider.hasValueNotEqualTo("unexpected").description());
    }

    private static Evaluation<Object> evaluate(
            PreservingCondition<Object> condition, Object actual) throws Exception {
        return RuntimeCondition.preserving(condition).evaluate(actual);
    }

    private static <T> Evaluation<T> evaluatePresent(Optional<T> actual)
            throws Exception {
        return RuntimeCondition.<T>present(ConditionProvider.present).evaluate(actual);
    }

    private static void assertSatisfied(Evaluation<?> evaluation, Object result) {
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(result, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertSatisfiedType(Evaluation<?> evaluation,
            Class<?> resultType) {
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertEquals(resultType, evaluation.result().getClass());
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

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class RightOperand {
        private int comparisons;

        @Override
        public boolean equals(Object other) {
            comparisons++;
            return false;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
