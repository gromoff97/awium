package io.github.gromoff97.assertility;

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

        assertSatisfied(AwaitConditions.isNull.evaluate(null), null);
        assertUnsatisfied(AwaitConditions.isNull.evaluate(actual),
                "value was not null");
        assertSatisfied(evaluate(AwaitConditions.isNotNull, actual), actual);
        assertUnsatisfied(evaluate(AwaitConditions.isNotNull, null),
                "value was null");
    }

    @Test
    void equalityConditionsAreComplementsAndReturnTheActualSnapshot()
            throws Exception {
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);

        assertSatisfied(evaluate(AwaitConditions.equalTo(expected), equalActual),
                equalActual);
        assertUnsatisfied(evaluate(AwaitConditions.notEqualTo(expected), equalActual),
                "value was equal");
        assertUnsatisfied(evaluate(AwaitConditions.equalTo(expected), differentActual),
                "value was not equal");
        assertSatisfied(evaluate(AwaitConditions.notEqualTo(expected), differentActual),
                differentActual);
        assertSatisfied(evaluate(AwaitConditions.equalTo(null), null), null);
        assertSatisfied(evaluate(AwaitConditions.notEqualTo(expected), null), null);
        assertSatisfiedType(evaluate(AwaitConditions.equalTo(
                new int[]{1, 2}), new int[]{1, 2}), int[].class);
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent()
            throws Exception {
        Optional<String> empty = Optional.empty();
        Optional<String> present = Optional.of("value");

        assertEquals("optional to remain present",
                ConditionAdapters.<String>present(AwaitConditions.present)
                        .description().get());
        assertUnsatisfied(evaluatePresent(null), "optional was null");
        assertUnsatisfied(AwaitConditions.absent.evaluate(null), "optional was null");
        assertUnsatisfied(evaluatePresent(empty), "optional was empty");
        assertSatisfied(AwaitConditions.absent.evaluate(empty), null);
        assertSatisfied(evaluatePresent(present), "value");
        assertUnsatisfied(AwaitConditions.absent.evaluate(present),
                "optional was present");
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue()
            throws Exception {
        var expected = new EqualValue(1);
        var equalActual = new EqualValue(1);
        var differentActual = new EqualValue(2);
        var equal = AwaitConditions.hasValueEqualTo(expected);
        var notEqual = AwaitConditions.hasValueNotEqualTo(expected);

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

        assertSatisfied(AwaitConditions.hasValueEqualTo((Object) expected)
                .evaluate(Optional.of(actual)), actual);
        assertEquals(1, actual.comparisons);
        assertEquals(0, expected.comparisons);

        int[] actualArray = {1, 2};
        assertSame(actualArray, AwaitConditions.hasValueEqualTo(
                new int[]{1, 2}).evaluate(Optional.of(actualArray)).result());
    }

    @Test
    void optionalValueFactoriesRejectNullOperandsImmediately() {
        assertEquals("expected must not be null", assertThrows(
                NullPointerException.class,
                () -> AwaitConditions.hasValueEqualTo(null)).getMessage());
        assertEquals("unexpected must not be null", assertThrows(
                NullPointerException.class,
                () -> AwaitConditions.hasValueNotEqualTo(null)).getMessage());
    }

    @Test
    void builtInObjectAndOptionalConditionsExposeExactDescriptions() {
        assertEquals("value to be null", AwaitConditions.isNull.description());
        assertEquals("value to be non-null",
                ConditionAdapters.preserving(AwaitConditions.isNotNull)
                        .description().get());
        assertEquals("value equal to expected",
                ConditionAdapters.preserving(AwaitConditions.equalTo("expected"))
                        .description().get());
        assertEquals("value not equal to unexpected",
                ConditionAdapters.preserving(
                        AwaitConditions.notEqualTo("unexpected"))
                        .description().get());
        assertEquals("optional to be absent",
                AwaitConditions.absent.description());
        assertEquals("optional value equal to expected",
                AwaitConditions.hasValueEqualTo("expected").description());
        assertEquals("optional value not equal to unexpected",
                AwaitConditions.hasValueNotEqualTo("unexpected").description());
    }

    private static Evaluation<Object> evaluate(
            PreservingCondition<Object> condition, Object actual) throws Exception {
        return ConditionAdapters.preserving(condition).evaluate(actual);
    }

    private static <T> Evaluation<T> evaluatePresent(Optional<T> actual)
            throws Exception {
        return ConditionAdapters.<T>present(AwaitConditions.present).evaluate(actual);
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
