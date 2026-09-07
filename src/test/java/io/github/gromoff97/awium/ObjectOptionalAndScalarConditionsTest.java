package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Source.OptionalSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectOptionalAndScalarConditionsTest {

    @Test
    void objectConditionsPreserveAndNarrowTypes() throws Exception {
        var pollingTime = new FakeTime(0);
        var actual = new Child();
        Source<Parent> source = () -> actual;

        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(Conditions.sameAs(actual)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(Conditions.notSameAs(new Child())));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(Conditions.in(actual, new Parent())));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(Conditions.notIn(new Parent())));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(Conditions.matches(value -> value instanceof Child)));

        Child child = await(source).usingTime(pollingTime, pollingTime).until(Conditions.instanceOf(Child.class));
        Child exact = await(source).usingTime(pollingTime, pollingTime).until(Conditions.exactInstanceOf(Child.class));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals(Unsatisfied.class, evaluate(
                Conditions.exactInstanceOf(Parent.class), actual).getClass());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        var pollingTime = new FakeTime(0);
        OptionalSource<Object> source = () -> Optional.of("ready");

        Object expected = await(source).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue((Object) "ready"));
        Object selected = await(source).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(value -> value.toString().startsWith("r")));
        String narrowed = await(source).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(instanceOf(String.class)));
        String nested = await((OptionalSource<String>) () -> Optional.of("ready")).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(StringConditions.startsWith("rea")));
        Integer transformed = await((OptionalSource<String>) () -> Optional.of("ready")).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(Conditions.yields(String::length)));

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        var pollingTime = new FakeTime(0);
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.greaterThan(4)));
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.atLeast(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.lessThan(6)));
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.atMost(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.between(5, 6)));
        assertEquals(5, await((Source<Integer>) () -> 5).usingTime(pollingTime, pollingTime).until(Conditions.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() throws Exception {
        var pollingTime = new FakeTime(0);
        String actual = "Ready 42";

        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.nonBlank));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.contains("Ready", "42")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.doesNotContain("failed")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.containsIgnoringCase("READY")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.startsWith("Ready")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.endsWith("42")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.matchesRegex("Ready \\d+")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.equalToIgnoringCase("ready 42")));
        assertSame(actual, await((Source<String>) () -> actual).usingTime(pollingTime, pollingTime).until(StringConditions.lengthBetween(1, 20)));
        assertEquals(Satisfied.class, evaluate(StringConditions.blank, " \n").getClass());
        assertEquals(Unsatisfied.class, evaluate(StringConditions.nonEmpty, "").getClass());
    }

    @Test
    void objectAndOptionalConditionsCoverBothTruthValues() throws Exception {
        Object actual = new String("ready");
        Object equal = new String("ready");

        assertStatus(Conditions.isNull, null, Satisfied.class);
        assertStatus(Conditions.isNull, actual, Unsatisfied.class);
        assertPreserving(Conditions.isNotNull, actual, null);
        assertExpected(Conditions.equalTo(equal), actual, "other");
        assertExpected(Conditions.notEqualTo(equal), "other", actual);
        assertExpected(Conditions.sameAs(actual), actual, equal);
        assertExpected(Conditions.notSameAs(equal), actual, equal);
        assertExpected(Conditions.in("other", equal), actual, new Object());
        assertExpected(Conditions.notIn("other"), actual, "other");
        assertPreserving(Conditions.matches(value -> value.toString().startsWith("r")), actual, "failed");
        assertStatus(Conditions.instanceOf(String.class), actual, Satisfied.class);
        assertStatus(Conditions.instanceOf(Integer.class), actual, Unsatisfied.class);
        assertStatus(Conditions.exactInstanceOf(String.class), actual, Satisfied.class);
        assertStatus(Conditions.exactInstanceOf(Object.class), actual, Unsatisfied.class);

        assertEquals(Satisfied.class,
                evaluate(OptionalConditions.present, Optional.of("ready")).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(OptionalConditions.present, Optional.empty()).getClass());
        assertStatus(OptionalConditions.absent, Optional.empty(), Satisfied.class);
        assertStatus(OptionalConditions.absent, Optional.of("ready"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.of("failed"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue("ready"), Optional.empty(), Unsatisfied.class);
        assertStatus(OptionalConditions.doesNotHaveValue("failed"), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.doesNotHaveValue("ready"), Optional.of("ready"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(value -> value.startsWith("r")), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(value -> value.startsWith("r")), Optional.of("failed"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(instanceOf(String.class)), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(instanceOf(String.class)), Optional.of(42), Unsatisfied.class);
    }

    @Test
    void comparableConditionsCoverBoundariesAndInvalidRanges() throws Exception {
        assertPreserving(Conditions.greaterThan(5), 6, 5);
        assertPreserving(Conditions.atLeast(5), 5, 4);
        assertPreserving(Conditions.lessThan(5), 4, 5);
        assertPreserving(Conditions.atMost(5), 5, 6);
        assertStatus(Conditions.between(5, 7), 5, Satisfied.class);
        assertStatus(Conditions.between(5, 7), 7, Satisfied.class);
        assertStatus(Conditions.between(5, 7), 4, Unsatisfied.class);
        assertStatus(Conditions.between(5, 7), 8, Unsatisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 6, Satisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 5, Unsatisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 7, Unsatisfied.class);
        assertStatus(Conditions.between(5, 5), 5, Satisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 5), 5, Unsatisfied.class);
        assertStatus(Conditions.greaterThan(5), null, Unsatisfied.class);

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
        assertStatus(StringConditions.containsIgnoringCase("ς"), "Σ", Satisfied.class);
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
        assertStatus(StringConditions.length(0), "", Satisfied.class);
        assertStatus(StringConditions.lengthAtMost(0), "", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "read", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failed", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "hey", Unsatisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failure", Unsatisfied.class);
        assertStatus(StringConditions.nonBlank, null, Unsatisfied.class);
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
        assertStatus(condition, matching, Satisfied.class);
        assertStatus(condition, mismatching, Unsatisfied.class);
    }

    private static <S, T extends S> void assertExpected(ExpectedCondition<T> condition, S matching, S mismatching) throws Exception {
        assertStatus(condition, matching, Satisfied.class);
        assertStatus(condition, mismatching, Unsatisfied.class);
    }

    private static <S> void assertStatus(Condition<? super S, ?> condition, S actual,
            Class<?> expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static <S> void assertStatus(PreservingCondition<? super S> condition, S actual,
            Class<?> expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static <S, T extends S> void assertStatus(ExpectedCondition<T> condition, S actual,
            Class<?> expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static <S, R> void assertStatus(NarrowingCondition<R> condition, S actual,
            Class<?> expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
