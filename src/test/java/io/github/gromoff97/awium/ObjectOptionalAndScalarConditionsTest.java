package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.ComparableCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ObjectCondition;
import io.github.gromoff97.awium.conditioning.conditions.OptionalCondition;
import io.github.gromoff97.awium.conditioning.conditions.StringCondition;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.OptionalSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectOptionalAndScalarConditionsTest {

    @Test
    void objectConditionsPreserveNarrowAndExtractTypes() throws Exception {
        var actual = new Child("ready");
        Source<Parent> source = () -> actual;

        assertSame(actual, await(source).until(ObjectCondition.sameAs(actual)));
        assertSame(actual, await(source).until(ObjectCondition.notSameAs(new Child("ready"))));
        assertSame(actual, await(source).until(ObjectCondition.in(actual, new Parent())));
        assertSame(actual, await(source).until(ObjectCondition.notIn(new Parent())));
        assertSame(actual, await(source).until(ObjectCondition.matches(value -> value instanceof Child)));

        Child child = await(source).until(ObjectCondition.instanceOf(Child.class));
        Child exact = await(source).until(ObjectCondition.exactInstanceOf(Child.class));
        String status = await(source).until(ObjectCondition.extracting(
                value -> ((Child) value).status(), ObjectCondition.equalTo("ready")));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals("ready", status);
        assertEquals(UNSATISFIED, evaluate(
                ObjectCondition.exactInstanceOf(Parent.class), actual).status());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        OptionalSource<Object> source = () -> Optional.of("ready");

        Object expected = await(source).until(OptionalCondition.hasValue((Object) "ready"));
        Object selected = await(source).until(OptionalCondition.hasValue(value -> value.toString().startsWith("r")));
        String narrowed = await(source).until(OptionalCondition.containsInstanceOf(String.class));
        String nested = await((OptionalSource<String>) () -> Optional.of("ready")).until(OptionalCondition.hasValue(StringCondition.startsWith("rea")));
        Integer transformed = await((OptionalSource<String>) () -> Optional.of("ready")).until(OptionalCondition.hasValue(Condition.yields(String::length)));

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.greaterThan(4)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.atLeast(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.lessThan(6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.atMost(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.between(5, 6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() throws Exception {
        String actual = "Ready 42";

        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.nonBlank));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.contains("Ready", "42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.doesNotContain("failed")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.containsIgnoringCase("READY")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.startsWith("Ready")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.endsWith("42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.matchesRegex("Ready \\d+")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.equalToIgnoringCase("ready 42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.lengthBetween(1, 20)));
        assertEquals(SATISFIED, evaluate(StringCondition.blank, " \n").status());
        assertEquals(UNSATISFIED, evaluate(StringCondition.nonEmpty, "").status());
    }

    @Test
    void objectAndOptionalConditionsCoverBothTruthValues() throws Exception {
        Object actual = new String("ready");
        Object equal = new String("ready");

        assertStatus(ObjectCondition.isNull, null, SATISFIED);
        assertStatus(ObjectCondition.isNull, actual, UNSATISFIED);
        assertPreserving(ObjectCondition.isNotNull, actual, null);
        assertPreserving(ObjectCondition.equalTo(equal), actual, "other");
        assertPreserving(ObjectCondition.notEqualTo(equal), "other", actual);
        assertPreserving(ObjectCondition.sameAs(actual), actual, equal);
        assertPreserving(ObjectCondition.notSameAs(equal), actual, equal);
        assertPreserving(ObjectCondition.in("other", equal), actual, new Object());
        assertPreserving(ObjectCondition.notIn("other"), actual, "other");
        assertPreserving(ObjectCondition.matches(value -> value.toString().startsWith("r")), actual, "failed");
        assertStatus(ObjectCondition.instanceOf(String.class), actual, SATISFIED);
        assertStatus(ObjectCondition.instanceOf(Integer.class), actual, UNSATISFIED);
        assertStatus(ObjectCondition.exactInstanceOf(String.class), actual, SATISFIED);
        assertStatus(ObjectCondition.exactInstanceOf(Object.class), actual, UNSATISFIED);

        assertEquals(SATISFIED,
                evaluate(OptionalCondition.present, Optional.of("ready")).status());
        assertEquals(UNSATISFIED,
                evaluate(OptionalCondition.present, Optional.empty()).status());
        assertStatus(OptionalCondition.absent, Optional.empty(), SATISFIED);
        assertStatus(OptionalCondition.absent, Optional.of("ready"), UNSATISFIED);
        assertStatus(OptionalCondition.hasValue("ready"), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalCondition.hasValue("ready"), Optional.of("failed"), UNSATISFIED);
        assertStatus(OptionalCondition.hasValue("ready"), Optional.empty(), UNSATISFIED);
        assertStatus(OptionalCondition.doesNotHaveValue("failed"), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalCondition.doesNotHaveValue("ready"), Optional.of("ready"), UNSATISFIED);
        assertStatus(OptionalCondition.hasValue(value -> value.startsWith("r")), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalCondition.hasValue(value -> value.startsWith("r")), Optional.of("failed"), UNSATISFIED);
        assertStatus(OptionalCondition.containsInstanceOf(String.class), Optional.of("ready"), SATISFIED);
        assertStatus(OptionalCondition.containsInstanceOf(String.class), Optional.of(42), UNSATISFIED);
    }

    @Test
    void comparableConditionsCoverBoundariesAndInvalidRanges() throws Exception {
        assertPreserving(ComparableCondition.greaterThan(5), 6, 5);
        assertPreserving(ComparableCondition.atLeast(5), 5, 4);
        assertPreserving(ComparableCondition.lessThan(5), 4, 5);
        assertPreserving(ComparableCondition.atMost(5), 5, 6);
        assertStatus(ComparableCondition.between(5, 7), 5, SATISFIED);
        assertStatus(ComparableCondition.between(5, 7), 7, SATISFIED);
        assertStatus(ComparableCondition.between(5, 7), 4, UNSATISFIED);
        assertStatus(ComparableCondition.between(5, 7), 8, UNSATISFIED);
        assertStatus(ComparableCondition.strictlyBetween(5, 7), 6, SATISFIED);
        assertStatus(ComparableCondition.strictlyBetween(5, 7), 5, UNSATISFIED);
        assertStatus(ComparableCondition.strictlyBetween(5, 7), 7, UNSATISFIED);
        assertStatus(ComparableCondition.greaterThan(5), null, UNSATISFIED);

        assertThrows(NullPointerException.class, () -> ComparableCondition.greaterThan(null));
        assertThrows(NullPointerException.class, () -> ComparableCondition.between(null, 1));
        assertThrows(NullPointerException.class, () -> ComparableCondition.between(1, null));
        assertThrows(IllegalArgumentException.class, () -> ComparableCondition.between(2, 1));
        assertThrows(IllegalArgumentException.class, () -> ComparableCondition.strictlyBetween(2, 1));
    }

    @Test
    void stringConditionsCoverBothTruthValuesAndBoundaries() throws Exception {
        assertPreserving(StringCondition.empty, "", "x");
        assertPreserving(StringCondition.nonEmpty, "x", "");
        assertPreserving(StringCondition.blank, " \n", "x");
        assertPreserving(StringCondition.nonBlank, "x", " \n");
        assertPreserving(StringCondition.contains("a", "b"), "abc", "ac");
        assertPreserving(StringCondition.doesNotContain("x", "y"), "abc", "ayc");
        assertPreserving(StringCondition.containsIgnoringCase("READY"), "ready", "failed");
        assertPreserving(StringCondition.startsWith("re"), "ready", "already");
        assertPreserving(StringCondition.doesNotStartWith("fail"), "ready", "failed");
        assertPreserving(StringCondition.endsWith("dy"), "ready", "read");
        assertPreserving(StringCondition.doesNotEndWith("ed"), "ready", "failed");
        assertPreserving(StringCondition.matchesRegex(Pattern.compile("r.*y")), "ready", "failed");
        assertPreserving(StringCondition.doesNotMatchRegex("f.*d"), "ready", "failed");
        assertPreserving(StringCondition.equalToIgnoringCase("READY"), "ready", "failed");
        assertPreserving(StringCondition.notEqualToIgnoringCase("FAILED"), "ready", "failed");
        assertPreserving(StringCondition.length(5), "ready", "read");
        assertPreserving(StringCondition.lengthIsNot(4), "ready", "read");
        assertPreserving(StringCondition.lengthGreaterThan(4), "ready", "read");
        assertPreserving(StringCondition.lengthAtLeast(5), "ready", "read");
        assertPreserving(StringCondition.lengthLessThan(6), "ready", "failed");
        assertPreserving(StringCondition.lengthAtMost(5), "ready", "failed");
        assertStatus(StringCondition.lengthBetween(4, 6), "read", SATISFIED);
        assertStatus(StringCondition.lengthBetween(4, 6), "failed", SATISFIED);
        assertStatus(StringCondition.lengthBetween(4, 6), "hey", UNSATISFIED);
        assertStatus(StringCondition.lengthBetween(4, 6), "failure", UNSATISFIED);
        assertStatus(StringCondition.nonBlank, null, UNSATISFIED);
        assertEquals("string length is 5", description(StringCondition.length(5)));
        assertEquals("string length was not 5",
                evaluate(StringCondition.length(5), "read").mismatch());

        assertEquals("length must be non-negative",
                assertThrows(IllegalArgumentException.class, () -> StringCondition.length(-1)).getMessage());
        assertEquals("length range must be non-negative and ordered",
                assertThrows(IllegalArgumentException.class,
                        () -> StringCondition.lengthBetween(-1, 1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> StringCondition.lengthBetween(2, 1));
        assertThrows(IllegalArgumentException.class, StringCondition::contains);
        assertThrows(NullPointerException.class, () -> StringCondition.contains((String[]) null));
        assertThrows(NullPointerException.class, () -> StringCondition.contains("ready", null));
    }

    private static <S> void assertPreserving(PreservingCondition<? super S> condition, S matching, S mismatching) throws Exception {
        assertStatus(condition, matching, SATISFIED);
        assertStatus(condition, mismatching, UNSATISFIED);
    }

    private static <S> void assertStatus(Condition<? super S, ?> condition, S actual,
            io.github.gromoff97.awium.conditioning.Evaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static <S> void assertStatus(PreservingStage<? super S> condition, S actual,
            io.github.gromoff97.awium.conditioning.Evaluation.Status expected) throws Exception {
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
