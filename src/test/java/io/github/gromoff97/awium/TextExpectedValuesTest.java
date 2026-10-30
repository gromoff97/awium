package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Conditions.captured;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.StringConditions.contains;
import static io.github.gromoff97.awium.StringConditions.containsAll;
import static io.github.gromoff97.awium.StringConditions.containsIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.contentEquals;
import static io.github.gromoff97.awium.StringConditions.doesNotContainAny;
import static io.github.gromoff97.awium.StringConditions.doesNotMatchRegex;
import static io.github.gromoff97.awium.StringConditions.equalToIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.matchesRegex;
import static io.github.gromoff97.awium.StringConditions.startsWith;
import static java.lang.Thread.currentThread;
import static java.util.regex.Pattern.compile;
import static java.util.stream.IntStream.range;

class TextExpectedValuesTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void blanknessReadsCodePointsOnTheCallerThreadWithoutRenderingText() {
        var caller = currentThread();
        record Text(String value) implements CharSequence {
            @Override public int length() { return value.length(); }
            @Override public char charAt(int index) { return value.charAt(index); }
            @Override public CharSequence subSequence(int start, int end) { return value.subSequence(start, end); }
            @Override public String toString() { throw new AssertionError("blankness must not render text"); }
        }
        for (String value : List.of("", " \t\n\u2000", "\u00a0", "\u2007", "\u202f", "\uD83D\uDE00", "\uD800", "ready")) {
            var text = new Text(value);
            var time = new FakeTime(0);
            var waiting = await(() -> text).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
            var matching = value.isBlank() ? StringConditions.blank : StringConditions.nonBlank;
            var mismatching = value.isBlank() ? StringConditions.nonBlank : StringConditions.blank;
            assertSame(text, waiting.until(matching));
            var failure = failure(diagnostic(waiting).until(mismatching));
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertNull(failure.cause());
        }
        CharSequence parallel = new CharSequence() {
            @Override public int length() { return 4096; }
            @Override public char charAt(int index) { return ' '; }
            @Override public CharSequence subSequence(int start, int end) { throw new AssertionError("slice not needed"); }
            @Override public String toString() { throw new AssertionError("blankness must not render text"); }
            @Override public IntStream codePoints() {
                return range(0, length()).parallel().map(index -> {
                    assertSame(caller, currentThread());
                    return charAt(index);
                });
            }
        };
        var waiting = await(() -> parallel).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
        assertSame(parallel, waiting.until(StringConditions.blank));
        var failure = failure(diagnostic(waiting).until(StringConditions.nonBlank));
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
    }

    @Test
    void characterChecksReadCharactersWithoutRenderingTheObservation() {
        var renderingFailure = new AssertionError("text must not be rendered for matching");
        var actual = new CharSequence() {
            private final String value = "Ready 42";
            @Override public int length() { return value.length(); }
            @Override public char charAt(int index) { return value.charAt(index); }
            @Override public CharSequence subSequence(int start, int end) { return value.subSequence(start, end); }
            @Override public String toString() { throw renderingFailure; }
        };
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
        for (var check : List.of(matchesRegex("Ready \\d+"),
                matchesRegex(compile("ready \\d+", Pattern.CASE_INSENSITIVE)),
                doesNotMatchRegex("failed.*"),
                doesNotMatchRegex(compile("failed.*")),
                containsIgnoringCase("READY"), contentEquals("Ready 42"))) {
            assertSame(actual, waiting.until(check.because("text is ready")));
            assertSame(actual, await(() -> Optional.of(actual)).until(hasValue(check)));
        }
        for (var check : List.of(matchesRegex("failed.*"),
                doesNotMatchRegex("Ready \\d+"), containsIgnoringCase("failed"),
                contentEquals("failed"))) {
            var failure = failure(diagnostic(waiting).until(check));
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertNull(failure.cause());
            assertEquals(List.of(renderingFailure), failure.suppressed());
        }
    }

    @Test
    void textFactoriesFreezeMutableExpectedTextAndPreserveTheSource() {
        record Check(Function<CharSequence, PreservingCondition<CharSequence>> factory, String matching, String mismatch) {}
        var checks = List.of(
                new Check(StringConditions::contains, "ready", "failed"),
                new Check(StringConditions::doesNotContain, "failed", "ready"),
                new Check(StringConditions::containsIgnoringCase, "READY", "failed"),
                new Check(StringConditions::startsWith, "ready now", "not ready"),
                new Check(StringConditions::doesNotStartWith, "not ready", "ready now"),
                new Check(StringConditions::endsWith, "already", "ready now"),
                new Check(StringConditions::doesNotEndWith, "ready now", "already"),
                new Check(StringConditions::matchesRegex, "ready", "ready now"),
                new Check(StringConditions::doesNotMatchRegex, "ready now", "ready"),
                new Check(StringConditions::contentEquals, "ready", "READY"),
                new Check(StringConditions::equalToIgnoringCase, "READY", "failed"),
                new Check(StringConditions::notEqualToIgnoringCase, "failed", "READY"));

        for (var check : checks) {
            var expected = new StringBuilder("ready");
            var condition = check.factory().apply(expected);
            expected.replace(0, expected.length(), "changed");
            var actual = new StringBuilder(check.matching());
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));

            var value = waiting.until(condition);
            assertSame(actual, value);
            assertEquals(actual.capacity(), value.capacity());
            AwaitResult<StringBuilder, StringBuilder> captured = diagnostic(waiting).until(condition);
            assertSame(actual, captured.orThrow());

            actual.replace(0, actual.length(), check.mismatch());
            var failure = failure(diagnostic(waiting).until(condition));
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertThat(failure.message()).doesNotContain("changed");
        }
    }

    @Test
    void contentEqualityComparesCharactersAndKeepsTheObservedObject() {
        var text = new StringBuilder("ready");
        var expected = CharBuffer.wrap("ready");
        var condition = contentEquals(expected);
        var values = await(() -> text).until(captured(condition, condition));
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());

        StringBuilder selected = await(() -> List.of(text)).until(singleElement(
                contentEquals(expected)));
        assertSame(text, selected);
        CharBuffer buffer = await(() -> expected).until(contentEquals(text));
        assertSame(expected, buffer);
        assertEquals(0, buffer.position());

        assertSame(text, await(() -> Map.of("status", text)).until(valueFor("status",
                contentEquals(expected))));
        assertTrue(await(() -> new StringBuilder()).until(contentEquals("")).isEmpty());

        var absent = tryAwait(() -> (StringBuilder) null).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).until(condition);
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure(absent).reason());
    }

    @Test
    void fragmentExpectationsFreezeTheIterableAndItsMutableText() {
        var prefix = new StringBuilder("rea");
        char[] suffix = {'d', 'y'};
        CharSequence[] expected = {prefix, CharBuffer.wrap(suffix)};
        var included = containsAll(java.util.Arrays.asList(expected));
        var excluded = doesNotContainAny(java.util.Arrays.asList(expected));
        prefix.replace(0, prefix.length(), "changed");
        suffix[0] = 'x';
        expected[1] = "changed";

        var text = new StringBuilder("ready");
        var values = await(() -> text).until(captured(included, included));
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());
        assertEquals(6, await(() -> "failed").until(excluded).length());

        var nested = startsWith(CharBuffer.wrap("rea"));
        assertEquals(text.capacity(), await(() -> Optional.of(text)).until(hasValue(nested)).capacity());
        assertEquals(text.capacity(), await(() -> List.of(text)).until(singleElement(nested)).capacity());
        assertEquals(text.capacity(), await(() -> Map.of("text", text)).until(valueFor("text", nested)).capacity());
    }

    @Test
    void emptyFragmentSetsMatchNonNullTextAndPreserveItsType() {
        for (String value : List.of("", "ready")) {
            var actual = new StringBuilder(value);
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            StringBuilder included = waiting.until(containsAll(List.of()));
            StringBuilder excluded = waiting.until(doesNotContainAny(List.of()));
            assertSame(actual, included);
            assertSame(actual, excluded);
            for (var check : List.of(containsAll(List.of()), doesNotContainAny(List.of()))) {
                assertSame(actual, diagnostic(waiting).until(check).orThrow());
                var absent = tryAwait(() -> (StringBuilder) null).usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(2)).until(check);
                assertEquals(AwaitFailure.Reason.TIMEOUT, failure(absent).reason());
            }
            var emptyFragment = diagnostic(waiting).until(doesNotContainAny(List.of("")));
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure(emptyFragment).reason());
        }
    }

    @Test
    void emptyFragmentSetsDoNotReadTheObservedText() {
        var actual = mock(CharSequence.class, invocation -> {
            throw new AssertionError("text must not be read: " + invocation.getMethod().getName());
        });
        for (var check : List.of(containsAll(List.of()), doesNotContainAny(List.of()))) {
            assertSame(actual, await(() -> actual).until(check));
        }
    }

    @Test
    void invalidExpectedTextIsRejectedDuringConstruction() {
        assertThrows(NullPointerException.class, () -> containsAll(null));
        assertThrows(NullPointerException.class, () -> containsAll(java.util.Arrays.asList("ready", null)));
        assertThrows(NullPointerException.class, () -> doesNotContainAny(null));
        var invalid = new CharSequence() {
            @Override public int length() { return 0; }
            @Override public char charAt(int index) { throw new IndexOutOfBoundsException(index); }
            @Override public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
            @Override public String toString() { return null; }
        };
        assertThrows(NullPointerException.class, () -> startsWith(invalid));
        assertThrows(NullPointerException.class, () -> contains(invalid));
        assertThrows(NullPointerException.class, () -> equalToIgnoringCase(invalid));
        assertThrows(NullPointerException.class, () -> contentEquals(invalid));
        assertThrows(NullPointerException.class, () -> contentEquals(null));
        assertThrows(NullPointerException.class, () -> matchesRegex(invalid));
    }
}
