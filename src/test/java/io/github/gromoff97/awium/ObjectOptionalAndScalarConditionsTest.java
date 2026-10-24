package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.OptionalSource;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectOptionalAndScalarConditionsTest {

    @Test
    void narrowingWaitsForANullSourceToProduceAValue() {
        for (var check : List.of(Conditions.instanceOf(String.class), Conditions.exactInstanceOf(String.class))) {
            var observations = java.util.Arrays.asList(null, "ready").iterator();
            var time = new FakeTime(0);

            String result = await(observations::next).usingTime(time, time)
                    .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(3))
                    .until(check.because("the value must arrive"));

            assertEquals("ready", result);
            assertFalse(observations.hasNext());
        }
    }

    @Test
    void unrelatedTypeChecksRemainOrdinaryMismatches() {
        for (var check : List.<Condition<? super String, ?>>of(Conditions.instanceOf(Integer.class), Conditions.exactInstanceOf(Integer.class))) {
            var time = new FakeTime(0);
            var waiting = await(() -> "actual").usingTime(time, time)
                    .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(2));
            var captured = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(check));
            assertEquals(AwaitFailure.Reason.TIMEOUT, captured.failure().reason());
            assertThrows(AwaitAssertionError.class, () -> waiting.until(check));
        }
        String actual = "actual";
        assertSame(actual, await(() -> actual).until(Conditions.notEqualTo("different")));
        assertSame(actual, await(() -> actual).until(Conditions.notSameAs("different")));
        assertSame(actual, await(() -> actual).until(Conditions.notIn("one", "two")));
    }

    @Test
    void objectConditionsPreserveAndNarrowTypes() {
        var pollingTime = new FakeTime(0);
        var actual = new Child();
        Callable<Parent> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(Conditions.sameAs(actual)));
        assertSame(actual, waiting.until(Conditions.notSameAs(new Child())));
        assertSame(actual, waiting.until(Conditions.in(actual, new Parent())));
        assertSame(actual, waiting.until(Conditions.notIn(new Parent())));
        assertSame(actual, waiting.until(Conditions.matches(value -> value instanceof Child)));

        Child child = waiting.until(Conditions.instanceOf(Child.class));
        Child exact = waiting.until(Conditions.exactInstanceOf(Child.class));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals(Unsatisfied.class, evaluate(
                Conditions.exactInstanceOf(Parent.class), actual).getClass());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        var pollingTime = new FakeTime(0);
        OptionalSource<Object> source = () -> Optional.of("ready");
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        Object expected = waiting.until(OptionalConditions.hasValue(Conditions.equalTo((Object) "ready")));
        Object selected = waiting.until(OptionalConditions.hasValue(Conditions.matches(value -> value.toString().startsWith("r"))));
        String narrowed = waiting.until(OptionalConditions.hasValue(instanceOf(String.class)));
        String nested = await(() -> Optional.of("ready")).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(StringConditions.startsWith("rea")));
        Integer transformed = await(() -> Optional.of("ready").map(String::length)).usingTime(pollingTime, pollingTime).until(OptionalConditions.present);

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        var pollingTime = new FakeTime(0);
        var waiting = await(() -> 5).usingTime(pollingTime, pollingTime);
        assertEquals(5, waiting.until(Conditions.greaterThan(4)));
        assertEquals(5, waiting.until(Conditions.atLeast(5)));
        assertEquals(5, waiting.until(Conditions.lessThan(6)));
        assertEquals(5, waiting.until(Conditions.atMost(5)));
        assertEquals(5, waiting.until(Conditions.between(5, 6)));
        assertEquals(5, waiting.until(Conditions.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() {
        var pollingTime = new FakeTime(0);
        String actual = "Ready 42";
        var waiting = await(() -> actual).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(StringConditions.nonBlank));
        assertSame(actual, waiting.until(StringConditions.contains("Ready", "42")));
        assertSame(actual, waiting.until(StringConditions.doesNotContain("failed")));
        assertSame(actual, waiting.until(StringConditions.containsIgnoringCase("READY")));
        assertSame(actual, waiting.until(StringConditions.startsWith("Ready")));
        assertSame(actual, waiting.until(StringConditions.endsWith("42")));
        assertSame(actual, waiting.until(StringConditions.matchesRegex("Ready \\d+")));
        assertSame(actual, waiting.until(StringConditions.equalToIgnoringCase("ready 42")));
        assertSame(actual, waiting.until(StringConditions.lengthBetween(1, 20)));
        assertEquals(Satisfied.class, evaluate(StringConditions.blank, " \n").getClass());
        assertEquals(Unsatisfied.class, evaluate(StringConditions.nonEmpty, "").getClass());
    }

    @Test
    void objectAndOptionalConditionsCoverBothTruthValues() {
        Object actual = new String("ready");
        Object equal = new String("ready");

        assertStatus(Conditions.isNull, null, Satisfied.class);
        assertStatus(Conditions.isNull, actual, Unsatisfied.class);
        assertPreserving(Conditions.isNotNull, actual, null);
        assertPreserving(Conditions.equalTo(equal), actual, "other");
        assertPreserving(Conditions.notEqualTo(equal), "other", actual);
        assertPreserving(Conditions.sameAs(actual), actual, equal);
        assertPreserving(Conditions.notSameAs(equal), actual, equal);
        assertPreserving(Conditions.in("other", equal), actual, new Object());
        assertPreserving(Conditions.notIn("other"), actual, "other");
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
        assertStatus(OptionalConditions.hasValue(Conditions.equalTo("ready")), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.equalTo("ready")), Optional.of("failed"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.equalTo("ready")), Optional.empty(), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.notEqualTo("failed")), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.notEqualTo("ready")), Optional.of("ready"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.matches((String value) -> value.startsWith("r"))), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(Conditions.matches((String value) -> value.startsWith("r"))), Optional.of("failed"), Unsatisfied.class);
        assertStatus(OptionalConditions.hasValue(instanceOf(String.class)), Optional.of("ready"), Satisfied.class);
        assertStatus(OptionalConditions.hasValue(instanceOf(String.class)), Optional.of(42), Unsatisfied.class);
    }

    @Test
    void comparableConditionsCoverBoundariesAndInvalidRanges() {
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
    void stringConditionsCoverBothTruthValuesAndBoundaries() {
        assertPreserving(StringConditions.empty, "", "x");
        assertPreserving(StringConditions.nonEmpty, "x", "");
        assertPreserving(StringConditions.blank, " \n", "x");
        assertPreserving(StringConditions.nonBlank, "x", " \n");
        assertPreserving(StringConditions.contains("a", "b"), "abc", "ac");
        assertPreserving(StringConditions.doesNotContain("x", "y"), "abc", "ayc");
        assertPreserving(StringConditions.containsIgnoringCase("READY"), "ready", "failed");
        assertStatus(StringConditions.containsIgnoringCase("ς"), "Σ", Satisfied.class);
        assertPreserving(StringConditions.containsIgnoringCase("a+b[.]"), "xA+B[.]y", "aaabxy");
        assertStatus(StringConditions.containsIgnoringCase("\uD800"), "\uD800\uDC00", Unsatisfied.class);
        assertStatus(StringConditions.containsIgnoringCase("\uD800"), "\uD800", Satisfied.class);
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
        assertStatus(StringConditions.length(0), null, Unsatisfied.class);
        assertStatus(StringConditions.lengthAtMost(0), "", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "read", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failed", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "hey", Unsatisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failure", Unsatisfied.class);
        assertStatus(StringConditions.nonBlank, null, Unsatisfied.class);
        assertEquals("string length is 5", description(StringConditions.length(5)));
        assertEquals("string length was 4",
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

    private static <S> void assertPreserving(Condition.ComparisonCondition<? extends S> condition, S matching, S mismatching) {
        assertEquals(Satisfied.class, evaluate(condition, matching).getClass());
        assertEquals(Unsatisfied.class, evaluate(condition, mismatching).getClass());
    }

    private static <S> void assertPreserving(PreservingCondition<? super S> condition, S matching, S mismatching) {
        assertStatus(condition, matching, Satisfied.class);
        assertStatus(condition, mismatching, Unsatisfied.class);
    }

    private static <S> void assertStatus(Condition.SelectedCondition<? super S> condition, S actual,
            Class<?> expected) {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static <S> void assertStatus(Condition<? super S, ?> condition, S actual,
            Class<?> expected) {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static <S> void assertStatus(PreservingCondition<? super S> condition, S actual,
            Class<?> expected) {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
