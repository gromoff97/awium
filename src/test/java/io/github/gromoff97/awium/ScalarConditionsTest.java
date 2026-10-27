package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarConditionsTest {

    @Test
    void singleBoundChecksDoNotRequireTheBoundToBeComparable() {
        record Limit(int number) {}
        record Measurement(int number) implements Comparable<Limit> {
            @Override
            public int compareTo(Limit limit) { return Integer.compare(number, limit.number()); }
        }
        var measurement = new Measurement(10);
        var time = new FakeTime(0);
        var waiting = await(() -> measurement).usingTime(time, time);
        var checks = List.of(Conditions.greaterThan(new Limit(5)),
                Conditions.atLeast(new Limit(10)),
                Conditions.lessThan(new Limit(15)),
                Conditions.atMost(new Limit(10)));

        for (var check : checks) {
            var value = waiting.until(check);
            AwaitResult<Measurement, Measurement> captured = waiting.tryUntil(check);
            var values = waiting.until(check, check);
            assertSame(measurement, value);
            assertEquals(10, value.number());
            assertSame(measurement, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
            assertEquals(10, values.getLast().number());
        }
        var selected = await(() -> Optional.of(measurement)).until(OptionalConditions.hasValue(
                Conditions.atLeast(new Limit(5))));
        var mapped = await(() -> Map.of("measurement", measurement)).until(MapConditions.valueFor("measurement",
                Conditions.lessThan(new Limit(15))));
        assertSame(measurement, selected);
        assertEquals(10, selected.number());
        assertSame(measurement, mapped);
        assertEquals(10, mapped.number());
    }

    @Test
    void comparableChecksKeepTheBaseSourceTypeWhenBoundsAreSubtypes() {
        class Score implements Comparable<Score> {
            private final int number;
            Score(int number) { this.number = number; }
            int number() { return number; }
            @Override
            public int compareTo(Score other) { return Integer.compare(number, other.number); }
        }
        class Bound extends Score {
            Bound(int number) { super(number); }
        }
        var score = new Score(10);
        var time = new FakeTime(0);
        var waiting = await(() -> score).usingTime(time, time);
        var checks = List.of(Conditions.greaterThan(new Bound(5)),
                Conditions.atLeast(new Bound(10)),
                Conditions.lessThan(new Bound(15)),
                Conditions.atMost(new Bound(10)),
                Conditions.between(new Bound(5), new Bound(15)),
                Conditions.strictlyBetween(new Bound(5), new Bound(15)));

        for (var check : checks) {
            var value = waiting.until(check);
            AwaitResult<Score, Score> result = waiting.tryUntil(check);
            var values = waiting.until(check, check);
            assertSame(score, value);
            assertEquals(10, value.number());
            assertSame(score, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
            assertSame(score, values.getFirst());
            assertEquals(10, values.getLast().number());
        }
    }

    @Test
    void comparableChecksAcceptObjectsComparableWithAnotherBoundType() {
        record Threshold(int number) implements Comparable<Threshold> {
            @Override
            public int compareTo(Threshold other) { return Integer.compare(number, other.number); }
        }
        record Reading(int number) implements Comparable<Threshold> {
            @Override
            public int compareTo(Threshold threshold) { return Integer.compare(number, threshold.number()); }
        }
        var reading = new Reading(10);
        var checks = List.of(Conditions.greaterThan(new Threshold(5)),
                Conditions.atLeast(new Threshold(10)),
                Conditions.lessThan(new Threshold(15)),
                Conditions.atMost(new Threshold(10)),
                Conditions.between(new Threshold(5), new Threshold(15)),
                Conditions.strictlyBetween(new Threshold(5), new Threshold(15)));

        for (var check : checks) {
            var value = await(() -> reading).until(check);
            assertSame(reading, value);
            assertEquals(10, value.number());
        }
        var selected = await(() -> List.of(reading)).until(CollectionConditions.singleElement(
                Conditions.greaterThan(new Threshold(5))));
        assertSame(reading, selected);
        assertEquals(10, selected.number());

        PreservingCondition<Integer> positive = Conditions.greaterThan(0);
        assertEquals(1, await(() -> 1).until(positive));
    }

    @Test
    void textEmptinessChecksDoNotRenderTheObservedCharacters() {
        record Text(int length) implements CharSequence {
            @Override public char charAt(int index) { throw new AssertionError("characters are not needed"); }
            @Override public CharSequence subSequence(int start, int end) { throw new AssertionError("slice is not needed"); }
            @Override public String toString() { throw new AssertionError("rendering is not needed for emptiness"); }
        }
        for (int length : List.of(0, 3)) {
            var text = new Text(length);
            var time = new FakeTime(0);
            var waiting = await(() -> text).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(2));
            var matching = length == 0 ? StringConditions.empty : StringConditions.nonEmpty;
            var mismatching = length == 0 ? StringConditions.nonEmpty : StringConditions.empty;

            assertSame(text, waiting.until(matching));
            assertSame(text, await(() -> Optional.of(text)).usingTime(time, time).until(OptionalConditions.hasValue(matching)));
            var failure = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(mismatching)).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        }
    }

    @Test
    void textChecksPreserveMutableSourcesInBothTerminalsAndSequences() {
        var text = new StringBuilder("Ready 42");
        var time = new FakeTime(0);
        var waiting = await(() -> text).usingTime(time, time);
        var checks = List.of(StringConditions.nonEmpty, StringConditions.nonBlank,
                StringConditions.containsAll(List.of("Ready", "42")), StringConditions.doesNotContain("failed"),
                StringConditions.containsIgnoringCase("READY"), StringConditions.startsWith("Ready"),
                StringConditions.doesNotStartWith("failed"), StringConditions.endsWith("42"),
                StringConditions.doesNotEndWith("failed"), StringConditions.matchesRegex("Ready \\d+"),
                StringConditions.doesNotMatchRegex("failed"), StringConditions.equalToIgnoringCase("ready 42"),
                StringConditions.notEqualToIgnoringCase("failed"), StringConditions.hasLength(8),
                StringConditions.hasLengthNotEqualTo(0), StringConditions.hasLengthGreaterThan(0),
                StringConditions.hasLengthAtLeast(8), StringConditions.hasLengthLessThan(9),
                StringConditions.hasLengthAtMost(8), StringConditions.hasLengthBetween(1, 10));

        for (var check : checks) {
            var value = waiting.until(check);
            assertSame(text, value);
            assertEquals(text.capacity(), value.capacity());
            var captured = waiting.tryUntil(check);
            switch (captured) {
                case AwaitResult.Satisfied(var attempts, var result) -> {
                    assertSame(text, result);
                    assertEquals(text.capacity(), result.capacity());
                }
                case AwaitResult.Failed(var attempts, var failure) -> throw new AssertionError(failure);
            }
        }

        var values = waiting.until(StringConditions.nonEmpty,
                StringConditions.equalToIgnoringCase("ready 42"));
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());
        text.setLength(0);
        assertSame(text, waiting.until(StringConditions.empty));
        text.append(" \t");
        assertSame(text, waiting.until(StringConditions.blank));
    }

    @Test
    void nestedTextChecksPreserveBuildersBuffersAndTheirCurrentContents() {
        var text = new StringBuilder("ready");
        var buffer = CharBuffer.wrap(" ready");
        buffer.position(1);

        var optional = await(() -> Optional.of(text)).until(
                OptionalConditions.hasValue(StringConditions.startsWith("rea")));
        var selected = await(() -> List.of(buffer)).until(
                CollectionConditions.singleElement(StringConditions.matchesRegex("ready")));
        var mapped = await(() -> Map.of("text", text)).until(
                MapConditions.valueFor("text", StringConditions.hasLength(5)));

        assertSame(text, optional);
        assertSame(buffer, selected);
        assertSame(text, mapped);
        assertEquals(text.capacity(), optional.capacity());
        assertEquals(1, selected.position());
        assertEquals(text.capacity(), mapped.capacity());

        text.replace(0, text.length(), "finished");
        assertSame(text, await(() -> text).until(StringConditions.endsWith("ed")));
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
        assertSame(actual, waiting.until(StringConditions.containsAll(List.of("Ready", "42"))));
        assertSame(actual, waiting.until(StringConditions.doesNotContain("failed")));
        assertSame(actual, waiting.until(StringConditions.containsIgnoringCase("READY")));
        assertSame(actual, waiting.until(StringConditions.startsWith("Ready")));
        assertSame(actual, waiting.until(StringConditions.endsWith("42")));
        assertSame(actual, waiting.until(StringConditions.matchesRegex("Ready \\d+")));
        assertSame(actual, waiting.until(StringConditions.equalToIgnoringCase("ready 42")));
        assertSame(actual, waiting.until(StringConditions.hasLengthBetween(1, 20)));
        assertEquals(Satisfied.class, evaluate(StringConditions.blank, " \n").getClass());
        assertEquals(Unsatisfied.class, evaluate(StringConditions.nonEmpty, "").getClass());
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
        assertPreserving(StringConditions.containsAll(List.of("a", "b")), "abc", "ac");
        assertPreserving(StringConditions.doesNotContainAny(List.of("x", "y")), "abc", "ayc");
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
        assertPreserving(StringConditions.hasLength(5), "ready", "read");
        assertPreserving(StringConditions.hasLengthNotEqualTo(4), "ready", "read");
        assertPreserving(StringConditions.hasLengthGreaterThan(4), "ready", "read");
        assertPreserving(StringConditions.hasLengthAtLeast(5), "ready", "read");
        assertPreserving(StringConditions.hasLengthLessThan(6), "ready", "failed");
        assertPreserving(StringConditions.hasLengthAtMost(5), "ready", "failed");
        assertStatus(StringConditions.hasLength(0), "", Satisfied.class);
        assertStatus(StringConditions.hasLength(0), null, Unsatisfied.class);
        assertStatus(StringConditions.hasLengthAtMost(0), "", Satisfied.class);
        assertStatus(StringConditions.hasLengthBetween(4, 6), "read", Satisfied.class);
        assertStatus(StringConditions.hasLengthBetween(4, 6), "failed", Satisfied.class);
        assertStatus(StringConditions.hasLengthBetween(4, 6), "hey", Unsatisfied.class);
        assertStatus(StringConditions.hasLengthBetween(4, 6), "failure", Unsatisfied.class);
        assertStatus(StringConditions.nonBlank, null, Unsatisfied.class);
        assertEquals("string length is 5", StringConditions.hasLength(5).description());
        assertEquals("string length was 4",
                mismatch(evaluate(StringConditions.hasLength(5), "read")));

        assertEquals("length must be non-negative",
                assertThrows(IllegalArgumentException.class, () -> StringConditions.hasLength(-1)).getMessage());
        assertEquals("length range must be non-negative and ordered",
                assertThrows(IllegalArgumentException.class,
                        () -> StringConditions.hasLengthBetween(-1, 1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> StringConditions.hasLengthBetween(2, 1));
        assertThrows(NullPointerException.class, () -> StringConditions.containsAll(null));
        assertThrows(NullPointerException.class, () -> StringConditions.containsAll(java.util.Arrays.asList("ready", null)));
    }

    private static <S> void assertPreserving(PreservingCondition<? super S> condition, S matching, S mismatching) {
        assertSame(matching, result(evaluate(condition, matching)));
        assertStatus(condition, mismatching, Unsatisfied.class);
    }

    private static <S> void assertStatus(PreservingCondition<? super S> condition, S actual,
            Class<?> expected) {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }


}
