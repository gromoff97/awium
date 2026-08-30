package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedStage;
import io.github.gromoff97.awium.condition.Condition.NarrowingStage;
import io.github.gromoff97.awium.conditions.OptionalConditions;
import io.github.gromoff97.awium.conditions.StringConditions;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.OptionalSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectOptionalAndScalarConditionsTest {

    @Test
    void objectConditionsPreserveAndNarrowTypes() throws Exception {
        var actual = new Child("ready");
        Source<Parent> source = () -> actual;

        assertSame(actual, await(source).until(Conditions.sameAs(actual)));
        assertSame(actual, await(source).until(Conditions.notSameAs(new Child("ready"))));
        assertSame(actual, await(source).until(Conditions.in(actual, new Parent())));
        assertSame(actual, await(source).until(Conditions.notIn(new Parent())));
        assertSame(actual, await(source).until(Conditions.matches(value -> value instanceof Child)));

        Child child = await(source).until(Conditions.instanceOf(Child.class));
        Child exact = await(source).until(Conditions.exactInstanceOf(Child.class));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals(UNSATISFIED, evaluate(
                Conditions.exactInstanceOf(Parent.class), actual).status());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        OptionalSource<Object> source = () -> Optional.of("ready");

        Object expected = await(source).until(OptionalConditions.hasValue((Object) "ready"));
        Object selected = await(source).until(OptionalConditions.hasValue(value -> value.toString().startsWith("r")));
        String narrowed = await(source).until(OptionalConditions.containsInstanceOf(String.class));
        String nested = await((OptionalSource<String>) () -> Optional.of("ready")).until(OptionalConditions.hasValue(StringConditions.startsWith("rea")));
        Integer transformed = await((OptionalSource<String>) () -> Optional.of("ready")).until(OptionalConditions.hasValue(Conditions.yields(String::length)));

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.greaterThan(4)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.atLeast(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.lessThan(6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.atMost(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.between(5, 6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(Conditions.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() throws Exception {
        String actual = "Ready 42";

        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.nonBlank));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.contains("Ready", "42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.doesNotContain("failed")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.containsIgnoringCase("READY")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.startsWith("Ready")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.endsWith("42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.matchesRegex("Ready \\d+")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.equalToIgnoringCase("ready 42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringConditions.lengthBetween(1, 20)));
        assertEquals(SATISFIED, evaluate(StringConditions.blank, " \n").status());
        assertEquals(UNSATISFIED, evaluate(StringConditions.nonEmpty, "").status());
    }

    @Test
    void objectAndOptionalConditionsCoverBothTruthValues() throws Exception {
        Object actual = new String("ready");
        Object equal = new String("ready");

        assertStatus(Conditions.isNull, null, SATISFIED);
        assertStatus(Conditions.isNull, actual, UNSATISFIED);
        assertPreserving(Conditions.isNotNull, actual, null);
        assertExpected(Conditions.equalTo(equal), actual, "other");
        assertExpected(Conditions.notEqualTo(equal), "other", actual);
        assertExpected(Conditions.sameAs(actual), actual, equal);
        assertExpected(Conditions.notSameAs(equal), actual, equal);
        assertExpected(Conditions.in("other", equal), actual, new Object());
        assertExpected(Conditions.notIn("other"), actual, "other");
        assertPreserving(Conditions.matches(value -> value.toString().startsWith("r")), actual, "failed");
        assertStatus(Conditions.instanceOf(String.class), actual, SATISFIED);
        assertStatus(Conditions.instanceOf(Integer.class), actual, UNSATISFIED);
        assertStatus(Conditions.exactInstanceOf(String.class), actual, SATISFIED);
        assertStatus(Conditions.exactInstanceOf(Object.class), actual, UNSATISFIED);

        assertEquals(SATISFIED,
                evaluate(OptionalConditions.present, Optional.of("ready")).status());
        assertEquals(UNSATISFIED,
                evaluate(OptionalConditions.present, Optional.empty()).status());
        assertStatus(OptionalConditions.absent, Optional.empty(), SATISFIED);
        assertStatus(OptionalConditions.absent, Optional.of("ready"), UNSATISFIED);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.of("failed"), UNSATISFIED);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.empty(), UNSATISFIED);
        assertStatus(OptionalConditions.doesNotHaveValue("failed"), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalConditions.doesNotHaveValue("ready"), Optional.of("ready"), UNSATISFIED);
        assertStatus(OptionalConditions.hasValue(value -> value.startsWith("r")), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalConditions.hasValue(value -> value.startsWith("r")), Optional.of("failed"), UNSATISFIED);
        assertStatus(OptionalConditions.containsInstanceOf(String.class), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalConditions.containsInstanceOf(String.class), Optional.of(42), UNSATISFIED);
    }

    @Test
    void comparableConditionsCoverBoundariesAndInvalidRanges() throws Exception {
        assertPreserving(Conditions.greaterThan(5), 6, 5);
        assertPreserving(Conditions.atLeast(5), 5, 4);
        assertPreserving(Conditions.lessThan(5), 4, 5);
        assertPreserving(Conditions.atMost(5), 5, 6);
        assertStatus(Conditions.between(5, 7), 5, SATISFIED);
        assertStatus(Conditions.between(5, 7), 7, SATISFIED);
        assertStatus(Conditions.between(5, 7), 4, UNSATISFIED);
        assertStatus(Conditions.between(5, 7), 8, UNSATISFIED);
        assertStatus(Conditions.strictlyBetween(5, 7), 6, SATISFIED);
        assertStatus(Conditions.strictlyBetween(5, 7), 5, UNSATISFIED);
        assertStatus(Conditions.strictlyBetween(5, 7), 7, UNSATISFIED);
        assertStatus(Conditions.between(5, 5), 5, SATISFIED);
        assertStatus(Conditions.strictlyBetween(5, 5), 5, UNSATISFIED);
        assertStatus(Conditions.greaterThan(5), null, UNSATISFIED);

        assertThrows(NullPointerException.class, () -> Conditions.greaterThan(null));
        assertThrows(NullPointerException.class, () -> Conditions.between(null, 1));
        assertThrows(NullPointerException.class, () -> Conditions.between(1, null));
        assertThrows(IllegalArgumentException.class, () -> Conditions.between(2, 1));
        assertThrows(IllegalArgumentException.class, () -> Conditions.strictlyBetween(2, 1));
    }

    @Test
    void stringConditionsCoverBothTruthValuesAndBoundaries() throws Exception {
        assertPreserving(StringConditions.empty, "", "x");
        assertPreserving(StringConditions.nonEmpty, "x", "");
        assertPreserving(StringConditions.blank, " \n", "x");
        assertPreserving(StringConditions.nonBlank, "x", " \n");
        assertPreserving(StringConditions.contains("a", "b"), "abc", "ac");
        assertPreserving(StringConditions.doesNotContain("x", "y"), "abc", "ayc");
        assertPreserving(StringConditions.containsIgnoringCase("READY"), "ready", "failed");
        assertStatus(StringConditions.containsIgnoringCase("ς"), "Σ", SATISFIED);
        assertPreserving(StringConditions.startsWith("re"), "ready", "already");
        assertPreserving(StringConditions.doesNotStartWith("fail"), "ready", "failed");
        assertPreserving(StringConditions.endsWith("dy"), "ready", "read");
        assertPreserving(StringConditions.doesNotEndWith("ed"), "ready", "failed");
        assertPreserving(StringConditions.matchesRegex(Pattern.compile("r.*y")), "ready", "failed");
        assertPreserving(StringConditions.doesNotMatchRegex("f.*d"), "ready", "failed");
        assertPreserving(StringConditions.equalToIgnoringCase("READY"), "ready", "failed");
        assertPreserving(StringConditions.notEqualToIgnoringCase("FAILED"), "ready", "failed");
        assertPreserving(StringConditions.length(5), "ready", "read");
        assertPreserving(StringConditions.lengthIsNot(4), "ready", "read");
        assertPreserving(StringConditions.lengthGreaterThan(4), "ready", "read");
        assertPreserving(StringConditions.lengthAtLeast(5), "ready", "read");
        assertPreserving(StringConditions.lengthLessThan(6), "ready", "failed");
        assertPreserving(StringConditions.lengthAtMost(5), "ready", "failed");
        assertStatus(StringConditions.length(0), "", SATISFIED);
        assertStatus(StringConditions.lengthAtMost(0), "", SATISFIED);
        assertStatus(StringConditions.lengthBetween(4, 6), "read", SATISFIED);
        assertStatus(StringConditions.lengthBetween(4, 6), "failed", SATISFIED);
        assertStatus(StringConditions.lengthBetween(4, 6), "hey", UNSATISFIED);
        assertStatus(StringConditions.lengthBetween(4, 6), "failure", UNSATISFIED);
        assertStatus(StringConditions.nonBlank, null, UNSATISFIED);
        assertEquals("string length is 5", description(StringConditions.length(5)));
        assertEquals("string length was not 5",
                mismatch(evaluate(StringConditions.length(5), "read")));

        assertEquals("length must be non-negative",
                assertThrows(IllegalArgumentException.class, () -> StringConditions.length(-1)).getMessage());
        assertEquals("length range must be non-negative and ordered",
                assertThrows(IllegalArgumentException.class,
                        () -> StringConditions.lengthBetween(-1, 1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> StringConditions.lengthBetween(2, 1));
        assertThrows(IllegalArgumentException.class, StringConditions::contains);
        assertThrows(NullPointerException.class, () -> StringConditions.contains((String[]) null));
        assertThrows(NullPointerException.class, () -> StringConditions.contains("ready", null));
    }

    private static <S> void assertPreserving(PreservingCondition<? super S> condition, S matching, S mismatching) throws Exception {
        assertStatus(condition, matching, SATISFIED);
        assertStatus(condition, mismatching, UNSATISFIED);
    }

    private static <S, T extends S> void assertExpected(ExpectedStage<T> condition, S matching, S mismatching) throws Exception {
        assertStatus(condition, matching, SATISFIED);
        assertStatus(condition, mismatching, UNSATISFIED);
    }

    private static <S> void assertStatus(Condition<? super S, ?> condition, S actual,
            io.github.gromoff97.awium.condition.ConditionEvaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static <S> void assertStatus(PreservingStage<? super S> condition, S actual,
            io.github.gromoff97.awium.condition.ConditionEvaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static <S, T extends S> void assertStatus(ExpectedStage<T> condition, S actual,
            io.github.gromoff97.awium.condition.ConditionEvaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static <S, R> void assertStatus(NarrowingStage<R> condition, S actual,
            io.github.gromoff97.awium.condition.ConditionEvaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
        private final String status;

        private Child(String status) {
            this.status = status;
        }

        private String status() {
            return status;
        }
    }
}
