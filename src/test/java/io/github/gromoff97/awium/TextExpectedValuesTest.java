package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
import static io.github.gromoff97.awium.MapConditions.awaitMap;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class TextExpectedValuesTest {

    @Test
    void textFactoriesFreezeMutableExpectedTextAndPreserveTheSource() {
        record Check(Function<CharSequence, PreservingCondition<CharSequence>> factory,
                String matching, String mismatch) {}
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
            var condition = check.factory().apply(expected).because("snapshot");
            expected.replace(0, expected.length(), "changed");
            var actual = new StringBuilder(check.matching());
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));

            var value = waiting.until(condition);
            assertSame(actual, value);
            assertEquals(actual.capacity(), value.capacity());
            AwaitResult<StringBuilder, StringBuilder> captured = waiting.tryUntil(condition);
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());

            actual.replace(0, actual.length(), check.mismatch());
            var failure = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(condition)).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertFalse(failure.message().contains("changed"));
        }
    }

    @Test
    void contentEqualityComparesCharactersAndKeepsTheObservedObject() {
        var text = new StringBuilder("ready");
        var expected = CharBuffer.wrap("ready");
        var condition = StringConditions.contentEquals(expected).because("same characters");
        var values = await(() -> text).until(condition, condition);
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());

        StringBuilder selected = awaitCollection(() -> List.of(text)).until(CollectionConditions.single(
                StringConditions.contentEquals(expected)).because("one matching text"));
        assertSame(text, selected);
        CharBuffer buffer = await(() -> expected).until(StringConditions.contentEquals(text).because("buffer"));
        assertSame(expected, buffer);
        assertEquals(0, buffer.position());

        assertSame(text, awaitMap(() -> Map.of("status", text)).until(MapConditions.valueFor("status",
                StringConditions.contentEquals(expected)).because("status text")));
        assertTrue(await(() -> new StringBuilder()).until(StringConditions.contentEquals("")).isEmpty());

        var time = new FakeTime(0);
        var absent = await(() -> (StringBuilder) null).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(condition);
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, absent).failure().reason());
    }

    @Test
    void fragmentExpectationsFreezeTheIterableAndItsMutableText() {
        var prefix = new StringBuilder("rea");
        char[] suffix = {'d', 'y'};
        CharSequence[] expected = {prefix, CharBuffer.wrap(suffix)};
        var included = StringConditions.containsAll(java.util.Arrays.asList(expected));
        var excluded = StringConditions.doesNotContainAny(java.util.Arrays.asList(expected));
        prefix.replace(0, prefix.length(), "changed");
        suffix[0] = 'x';
        expected[1] = "changed";

        var text = new StringBuilder("ready");
        var values = await(() -> text).until(included.because("first"), included.because(() -> "second"));
        assertSame(text, values.getFirst());
        assertEquals(text.capacity(), values.getLast().capacity());
        assertEquals(6, await(() -> "failed").until(excluded).length());

        var nested = StringConditions.startsWith(CharBuffer.wrap("rea")).because("prefix");
        assertEquals(text.capacity(), awaitOptional(() -> Optional.of(text)).until(OptionalConditions.hasValue(nested)).capacity());
        assertEquals(text.capacity(), awaitCollection(() -> List.of(text)).until(CollectionConditions.single(nested)).capacity());
        assertEquals(text.capacity(), awaitMap(() -> Map.of("text", text)).until(MapConditions.valueFor("text", nested)).capacity());
    }

    @Test
    void invalidExpectedTextIsRejectedDuringConstruction() {
        assertThrows(NullPointerException.class, () -> StringConditions.containsAll(null));
        assertThrows(NullPointerException.class, () -> StringConditions.containsAll(java.util.Arrays.asList("ready", null)));
        assertThrows(IllegalArgumentException.class, () -> StringConditions.containsAll(List.of()));
        var invalid = new CharSequence() {
            @Override public int length() { return 0; }
            @Override public char charAt(int index) { throw new IndexOutOfBoundsException(index); }
            @Override public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
            @Override public String toString() { return null; }
        };
        assertThrows(NullPointerException.class, () -> StringConditions.startsWith(invalid));
        assertThrows(NullPointerException.class, () -> StringConditions.contains(invalid));
        assertThrows(NullPointerException.class, () -> StringConditions.equalToIgnoringCase(invalid));
        assertThrows(NullPointerException.class, () -> StringConditions.contentEquals(invalid));
        assertThrows(NullPointerException.class, () -> StringConditions.contentEquals(null));
        assertThrows(NullPointerException.class, () -> StringConditions.matchesRegex(invalid));
    }
}
