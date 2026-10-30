package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.atLeast;
import static io.github.gromoff97.awium.Conditions.atMost;
import static io.github.gromoff97.awium.Conditions.between;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.greaterThan;
import static io.github.gromoff97.awium.Conditions.lessThan;
import static io.github.gromoff97.awium.Conditions.strictlyBetween;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.StringConditions.blank;
import static io.github.gromoff97.awium.StringConditions.containsAll;
import static io.github.gromoff97.awium.StringConditions.containsIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.doesNotContain;
import static io.github.gromoff97.awium.StringConditions.doesNotContainAny;
import static io.github.gromoff97.awium.StringConditions.doesNotEndWith;
import static io.github.gromoff97.awium.StringConditions.doesNotMatchRegex;
import static io.github.gromoff97.awium.StringConditions.doesNotStartWith;
import static io.github.gromoff97.awium.StringConditions.empty;
import static io.github.gromoff97.awium.StringConditions.endsWith;
import static io.github.gromoff97.awium.StringConditions.equalToIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.hasLength;
import static io.github.gromoff97.awium.StringConditions.hasLengthAtLeast;
import static io.github.gromoff97.awium.StringConditions.hasLengthAtMost;
import static io.github.gromoff97.awium.StringConditions.hasLengthBetween;
import static io.github.gromoff97.awium.StringConditions.hasLengthGreaterThan;
import static io.github.gromoff97.awium.StringConditions.hasLengthLessThan;
import static io.github.gromoff97.awium.StringConditions.hasLengthNotEqualTo;
import static io.github.gromoff97.awium.StringConditions.matchesRegex;
import static io.github.gromoff97.awium.StringConditions.nonBlank;
import static io.github.gromoff97.awium.StringConditions.nonEmpty;
import static io.github.gromoff97.awium.StringConditions.notEqualToIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.startsWith;
import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static java.lang.Integer.compare;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ScalarConditionsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void singleBoundChecksDoNotRequireTheBoundToBeComparable() {
        record Limit(int number) {}
        record Measurement(int number) implements Comparable<Limit> {
            @Override
            public int compareTo(Limit limit) { return compare(number, limit.number()); }
        }
        var measurement = new Measurement(10);
        var waiting = await(() -> measurement).usingTime(time, time);
        var checks = List.of(greaterThan(new Limit(5)),
                atLeast(new Limit(10)),
                lessThan(new Limit(15)),
                atMost(new Limit(10)));

        for (var check : checks) {
            var value = waiting.until(check);
            AwaitResult<Measurement, Measurement> captured = diagnostic(waiting).until(check);
            var values = waiting.until(captured(check, check));
            assertSame(measurement, value);
            assertEquals(10, value.number());
            assertSame(measurement, captured.orThrow());
            assertEquals(10, values.getLast().number());
        }
        var selected = await(() -> Optional.of(measurement)).until(hasValue(
                atLeast(new Limit(5))));
        var mapped = await(() -> Map.of("measurement", measurement)).until(valueFor("measurement",
                lessThan(new Limit(15))));
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
            public int compareTo(Score other) { return compare(number, other.number); }
        }
        class Bound extends Score {
            Bound(int number) { super(number); }
        }
        var score = new Score(10);
        var waiting = await(() -> score).usingTime(time, time);
        var checks = List.of(greaterThan(new Bound(5)),
                atLeast(new Bound(10)),
                lessThan(new Bound(15)),
                atMost(new Bound(10)),
                between(new Bound(5), new Bound(15)),
                strictlyBetween(new Bound(5), new Bound(15)));

        for (var check : checks) {
            var value = waiting.until(check);
            AwaitResult<Score, Score> result = diagnostic(waiting).until(check);
            var values = waiting.until(captured(check, check));
            assertSame(score, value);
            assertEquals(10, value.number());
            assertSame(score, result.orThrow());
            assertSame(score, values.getFirst());
            assertEquals(10, values.getLast().number());
        }
    }

    @Test
    void comparableChecksAcceptObjectsComparableWithAnotherBoundType() {
        record Threshold(int number) implements Comparable<Threshold> {
            @Override
            public int compareTo(Threshold other) { return compare(number, other.number); }
        }
        record Reading(int number) implements Comparable<Threshold> {
            @Override
            public int compareTo(Threshold threshold) { return compare(number, threshold.number()); }
        }
        var reading = new Reading(10);
        var checks = List.of(greaterThan(new Threshold(5)),
                atLeast(new Threshold(10)),
                lessThan(new Threshold(15)),
                atMost(new Threshold(10)),
                between(new Threshold(5), new Threshold(15)),
                strictlyBetween(new Threshold(5), new Threshold(15)));

        for (var check : checks) {
            var value = await(() -> reading).until(check);
            assertSame(reading, value);
            assertEquals(10, value.number());
        }
        var selected = await(() -> List.of(reading)).until(singleElement(
                greaterThan(new Threshold(5))));
        assertSame(reading, selected);
        assertEquals(10, selected.number());

        PreservingCondition<Integer> positive = greaterThan(0);
        assertEquals(1, await(() -> 1).until(positive));
    }

    @ParameterizedTest
    @MethodSource("matchingTextLengths")
    void textEmptinessChecksDoNotRenderTheObservedCharacters(int length, PreservingCondition<CharSequence> check) {
        var text = new Text(length);
        var waiting = await(() -> text).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        assertSame(text, waiting.until(check));
        assertSame(text, await(() -> Optional.of(text)).usingTime(time, time).until(hasValue(check)));
    }

    @ParameterizedTest
    @MethodSource("mismatchingTextLengths")
    void textEmptinessMismatchesRemainTimeoutsWhenRenderingFails(int length, PreservingCondition<CharSequence> check) {
        var text = new Text(length);
        var waiting = await(() -> text).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        assertEquals(TIMEOUT, failure(diagnostic(waiting).until(check)).reason());
    }

    @Test
    void textChecksPreserveSourceTypesInBothTerminalsAndSequences() {
        var text = new StringBuilder("Ready 42");
        String immutable = text.toString();
        var waiting = await(() -> text).usingTime(time, time);
        var immutableWaiting = await(() -> immutable).usingTime(time, time);
        var checks = List.of(nonEmpty, nonBlank,
                containsAll(List.of("Ready", "42")), doesNotContain("failed"),
                containsIgnoringCase("READY"), startsWith("Ready"),
                doesNotStartWith("failed"), endsWith("42"),
                doesNotEndWith("failed"), matchesRegex("Ready \\d+"),
                doesNotMatchRegex("failed"), equalToIgnoringCase("ready 42"),
                notEqualToIgnoringCase("failed"), hasLength(8),
                hasLengthNotEqualTo(0), hasLengthGreaterThan(0),
                hasLengthAtLeast(8), hasLengthLessThan(9),
                hasLengthAtMost(8), hasLengthBetween(1, 10));

        for (var check : checks) {
            var retained = immutableWaiting.until(check);
            assertSame(immutable, retained);
            assertEquals("Ready", retained.substring(0, 5));
            var value = waiting.until(check);
            assertSame(text, value);
            assertEquals(text.capacity(), value.capacity());
            var captured = diagnostic(waiting).until(check);
            switch (captured) {
                case AwaitResult.Satisfied(var history, var result) -> {
                    assertSame(text, result);
                    assertEquals(text.capacity(), result.capacity());
                }
                case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure);
            }
        }

        var values = waiting.until(captured(nonEmpty,
                equalToIgnoringCase("ready 42")));
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());
        text.setLength(0);
        assertSame(text, waiting.until(empty));
        text.append(" \t");
        assertSame(text, waiting.until(blank));
    }

    @Test
    void nestedTextChecksPreserveBuildersBuffersAndTheirCurrentContents() {
        var text = new StringBuilder("ready");
        var buffer = CharBuffer.wrap(" ready");
        buffer.position(1);

        var optional = await(() -> Optional.of(text)).until(
                hasValue(startsWith("rea")));
        var selected = await(() -> List.of(buffer)).until(
                singleElement(matchesRegex("ready")));
        var mapped = await(() -> Map.of("text", text)).until(
                valueFor("text", hasLength(5)));

        assertSame(text, optional);
        assertSame(buffer, selected);
        assertSame(text, mapped);
        assertEquals(text.capacity(), optional.capacity());
        assertEquals(1, selected.position());
        assertEquals(text.capacity(), mapped.capacity());

        text.replace(0, text.length(), "finished");
        assertSame(text, await(() -> text).until(endsWith("ed")));
    }

    @ParameterizedTest
    @MethodSource("comparableChecks")
    void comparableRelationsPreserveTheValue(PreservingCondition<Integer> check) {
        assertEquals(5, await(() -> 5).usingTime(time, time).until(check));
    }

    @TestFactory
    List<DynamicNode> comparableBoundariesAndInvalidRanges() {
        return List.of(
                preserving(greaterThan(5), 6, 5),
                preserving(atLeast(5), 5, 4),
                preserving(lessThan(5), 4, 5),
                preserving(atMost(5), 5, 6),
                status(between(5, 7), 5, Satisfied.class),
                status(between(5, 7), 7, Satisfied.class),
                status(between(5, 7), 4, Unsatisfied.class),
                status(between(5, 7), 8, Unsatisfied.class),
                status(strictlyBetween(5, 7), 6, Satisfied.class),
                status(strictlyBetween(5, 7), 5, Unsatisfied.class),
                status(strictlyBetween(5, 7), 7, Unsatisfied.class),
                status(between(5, 5), 5, Satisfied.class),
                status(strictlyBetween(5, 5), 5, Unsatisfied.class),
                status(greaterThan(5), null, Unsatisfied.class),
                dynamicTest("reject null single bound", () -> assertThrows(NullPointerException.class, () -> greaterThan(null))),
                dynamicTest("reject null lower bound", () -> assertThrows(NullPointerException.class, () -> between(null, 1))),
                dynamicTest("reject null upper bound", () -> assertThrows(NullPointerException.class, () -> between(1, null))),
                dynamicTest("reject reversed inclusive range", () -> assertThrows(IllegalArgumentException.class, () -> between(2, 1))),
                dynamicTest("reject reversed exclusive range", () -> assertThrows(IllegalArgumentException.class, () -> strictlyBetween(2, 1))));
    }

    @TestFactory
    List<DynamicNode> textTruthValuesAndBoundaries() {
        return List.of(
                preserving(empty, "", "x"),
                preserving(nonEmpty, "x", ""),
                preserving(blank, " \n", "x"),
                preserving(nonBlank, "x", " \n"),
                preserving(containsAll(List.of("a", "b")), "abc", "ac"),
                preserving(doesNotContainAny(List.of("x", "y")), "abc", "ayc"),
                preserving(containsIgnoringCase("READY"), "ready", "failed"),
                status(containsIgnoringCase("ς"), "Σ", Satisfied.class),
                preserving(containsIgnoringCase("a+b[.]"), "xA+B[.]y", "aaabxy"),
                status(containsIgnoringCase("\uD800"), "\uD800\uDC00", Unsatisfied.class),
                status(containsIgnoringCase("\uD800"), "\uD800", Satisfied.class),
                preserving(startsWith("re"), "ready", "already"),
                preserving(doesNotStartWith("fail"), "ready", "failed"),
                preserving(endsWith("dy"), "ready", "read"),
                preserving(doesNotEndWith("ed"), "ready", "failed"),
                preserving(matchesRegex(compile("r.*y")), "ready", "failed"),
                preserving(doesNotMatchRegex("f.*d"), "ready", "failed"),
                preserving(equalToIgnoringCase("READY"), "ready", "failed"),
                preserving(notEqualToIgnoringCase("FAILED"), "ready", "failed"),
                preserving(hasLength(5), "ready", "read"),
                preserving(hasLengthNotEqualTo(4), "ready", "read"),
                preserving(hasLengthGreaterThan(4), "ready", "read"),
                preserving(hasLengthAtLeast(5), "ready", "read"),
                preserving(hasLengthLessThan(6), "ready", "failed"),
                preserving(hasLengthAtMost(5), "ready", "failed"),
                status(hasLength(0), "", Satisfied.class),
                status(hasLength(0), null, Unsatisfied.class),
                status(hasLengthAtMost(0), "", Satisfied.class),
                status(hasLengthBetween(4, 6), "read", Satisfied.class),
                status(hasLengthBetween(4, 6), "failed", Satisfied.class),
                status(hasLengthBetween(4, 6), "hey", Unsatisfied.class),
                status(hasLengthBetween(4, 6), "failure", Unsatisfied.class),
                status(nonBlank, null, Unsatisfied.class),
                dynamicTest("length description identifies its bound",
                        () -> assertEquals("string length is 5", hasLength(5).description())),
                dynamicTest("length mismatch identifies the actual length",
                        () -> assertEquals("string length was 4", mismatch(evaluate(hasLength(5), "read")))),
                dynamicTest("reject negative length", () -> assertEquals("length must be non-negative",
                        assertThrows(IllegalArgumentException.class, () -> hasLength(-1)).getMessage())),
                dynamicTest("reject negative length range", () -> assertEquals("length range must be non-negative and ordered",
                        assertThrows(IllegalArgumentException.class, () -> hasLengthBetween(-1, 1)).getMessage())),
                dynamicTest("reject reversed length range", () -> assertThrows(IllegalArgumentException.class, () -> hasLengthBetween(2, 1))),
                dynamicTest("reject null expected collection", () -> assertThrows(NullPointerException.class, () -> containsAll(null))),
                dynamicTest("reject null expected text", () -> assertThrows(NullPointerException.class, () -> containsAll(asList("ready", null)))));
    }

    private static List<Arguments> matchingTextLengths() {
        return List.of(arguments(0, named("empty", empty)), arguments(3, named("nonEmpty", nonEmpty)));
    }

    private static List<Arguments> mismatchingTextLengths() {
        return List.of(arguments(0, named("nonEmpty", nonEmpty)), arguments(3, named("empty", empty)));
    }

    private record Text(int length) implements CharSequence {
        @Override public char charAt(int index) { throw new AssertionError("characters are not needed"); }
        @Override public CharSequence subSequence(int start, int end) { throw new AssertionError("slice is not needed"); }
        @Override public String toString() { throw new AssertionError("rendering is not needed for emptiness"); }
    }

    private static List<Named<PreservingCondition<Integer>>> comparableChecks() {
        return List.of(named("greaterThan(4)", greaterThan(4)), named("atLeast(5)", atLeast(5)),
                named("lessThan(6)", lessThan(6)), named("atMost(5)", atMost(5)),
                named("between(5, 6)", between(5, 6)), named("strictlyBetween(4, 6)", strictlyBetween(4, 6)));
    }

    private static <VALUE> DynamicContainer preserving(PreservingCondition<? super VALUE> condition, VALUE matching, VALUE mismatching) {
        var label = condition.description() + " " + condition.references();
        return dynamicContainer(label, List.of(
                dynamicTest(label + " / preserves: " + matching, () -> assertSame(matching, result(evaluate(condition, matching)))),
                dynamicTest(label + " / rejects: " + mismatching, () -> assertInstanceOf(Unsatisfied.class, evaluate(condition, mismatching)))));
    }

    private static <VALUE> DynamicTest status(PreservingCondition<? super VALUE> condition, VALUE actual, Class<?> expected) {
        return dynamicTest(condition.description() + " " + condition.references() + " / actual: " + actual + " / " + expected.getSimpleName(),
                () -> assertEquals(expected, evaluate(condition, actual).getClass()));
    }
}
