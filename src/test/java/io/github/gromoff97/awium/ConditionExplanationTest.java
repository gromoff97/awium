package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.function.CheckedPredicate.not;

class ConditionExplanationTest {

    private final FakeTime time = new FakeTime(0);

    private record User(boolean active) {}

    @Test
    void reasonsAreIndependentCopiesForEveryConditionFamily() {
        for (Condition original : List.of(isNotNull, equalTo("ready"), present, singleElement,
                valueFor("id"), instanceOf(User.class), captured(isNotNull), captured(equalTo("ready")),
                captured(present), captured(valueFor("id")), captured(instanceOf(User.class)))) {
            var first = original.because("first reason");
            var second = first.because("second reason");

            assertNotSame(original, first);
            assertNotSame(first, second);
            assertEquals(original.getClass(), second.getClass());
            assertNull(original.explanation());
            assertEquals("first reason", first.explanation());
            assertEquals("second reason", second.explanation());
            assertEquals(original.description(), second.description());
            assertEquals(original.references(), second.references());
            assertThrows(NullPointerException.class, () -> original.because(null));
            assertThrows(IllegalArgumentException.class, () -> original.because(" \n\t"));
        }
    }

    @Test
    void typedLambdasAndMethodReferencesKeepTheFluentResults() {
        var user = new User(true);
        var users = new ArrayList<>(List.of(user));
        Await.CollectionSource<ArrayList<User>> source = () -> users;
        Await.OptionalSource<User> optional = () -> Optional.of(user);
        Await.MapSource<Map<String, User>> map = () -> Map.of("id", user);

        ArrayList<User> checked = await(source).until(all((User value) -> value.active()).because("active users required"));
        ArrayList<User> referenced = await(source).until(all(User::active).because("active users required"));
        User selected = await(source).until(singleElement((User value) -> value.active()).because("one active user required"));
        User only = await(source).until(singleElement.because("one user required"));
        User found = await(optional).until(present.because("user must exist"));
        User mapped = await(map).until(valueFor("id", (User value) -> value.active()).because("user must be active"));
        User narrowed = await(() -> (Object) user).until(instanceOf(User.class).because("user value required"));
        Condition.PreservingCondition<User> check = matches(User::active);
        User direct = await(() -> user).until(check.because("active user required"));

        assertSame(users, checked);
        assertSame(users, referenced);
        assertThat(List.of(selected, only, found, mapped, narrowed, direct))
                .allSatisfy(result -> assertSame(user, result));
    }

    @Test
    void wildcardEntryChecksKeepTheMapTypeAndInheritedReason() {
        CheckedPredicate<Map.Entry<String, ? extends Number>> positive = entry -> entry.getValue().intValue() > 0;
        var check = matches(positive).because("entry requirement");
        var opposite = matches(not(positive)).because("entry requirement");
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("ready", 1);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        List<LinkedHashMap<String, Integer>> checked = List.of(
                waiting.until(allEntries(check)),
                waiting.until(hasEntry(check)),
                waiting.until(hasNoEntry(opposite)));
        checked.forEach(value -> assertSame(actual, value));

        for (var result : List.of(diagnostic(waiting).until(allEntries(opposite)),
                diagnostic(waiting).until(hasEntry(opposite)), diagnostic(waiting).until(hasNoEntry(check)))) {
            var failure = failure(result);
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertThat(failure.message()).contains("Because: entry requirement");
            assertEquals("entry requirement", result.conditions().getLast().condition().explanation());
        }
    }

    @Test
    void sequenceFailureIdentifiesTheStageAndItsReasonInBothTerminals() {
        var waiting = await(() -> "created").usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        var first = equalTo("created").because("payment must be created");
        var second = equalTo("paid").because("payment must be completed");

        var sequence = captured(first, second).because("checkout lifecycle");
        var result = diagnostic(waiting).until(sequence);
        var failure = failure(result);
        var thrown = assertThrows(AwaitAssertionError.class, () -> waiting.until(sequence));

        assertEquals(thrown.getMessage(), failure.message());
        assertThat(failure.message()).contains("stage 2/2", "Expected: \"paid\"",
                "Because: payment must be completed", "Because: checkout lifecycle",
                "Mismatch: value was not equal").doesNotContain("payment must be created");
        assertEquals(second.explanation(), result.conditions().getLast().condition().explanation());
    }

    @ParameterizedTest(name = "override={0}, present={1}")
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void composedChecksInheritReasonsAndAllowAnOuterOverride(boolean override, boolean present) {
        var nested = equalTo("ready").because("inner reason");
        var optional = hasValue(nested);
        var mapped = valueFor("id", nested);
        var selected = singleElement(nested);
        var quantified = all(nested);
        if (override) {
            optional = optional.because("outer reason");
            mapped = mapped.because("outer reason");
            selected = selected.because("outer reason");
            quantified = quantified.because("outer reason");
        }
        String expected = override ? "outer reason" : "inner reason";
        Optional<String> value = present ? Optional.of("pending") : Optional.empty();
        Map<String, String> entries = present ? Map.of("id", "pending") : Map.of();
        List<String> values = present ? List.of("pending") : List.of();
        var results = List.of(
                tryAwait(() -> value).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(optional),
                tryAwait(() -> entries).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(mapped),
                tryAwait(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(selected),
                tryAwait(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(quantified));
        for (var result : results) {
            var failure = failure(result);
            assertThat(failure.message()).contains("Because: " + expected);
            assertEquals(expected, result.conditions().getLast().condition().explanation());
            if (override) assertThat(failure.message()).doesNotContain("inner reason");
        }
        assertEquals("inner reason", nested.explanation());
    }
}
