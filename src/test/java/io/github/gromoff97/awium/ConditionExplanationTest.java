package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.*;

class ConditionExplanationTest {

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
            assertEquals(original.reference(), second.reference());
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
        for (User result : List.of(selected, only, found, mapped, narrowed, direct)) {
            assertSame(user, result);
        }
    }

    @Test
    void wildcardEntryChecksKeepTheMapTypeAndInheritedReason() {
        CheckedPredicate<Map.Entry<String, ? extends Number>> positive = entry -> entry.getValue().intValue() > 0;
        var check = matches(positive).because("entry requirement");
        var opposite = matches(CheckedPredicate.not(positive)).because("entry requirement");
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("ready", 1);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        List<LinkedHashMap<String, Integer>> checked = List.of(
                waiting.until(MapConditions.allEntries(check)),
                waiting.until(MapConditions.hasEntry(check)),
                waiting.until(MapConditions.hasNoEntry(opposite)));
        checked.forEach(value -> assertSame(actual, value));

        for (var result : List.of(diagnostic(waiting).until(MapConditions.allEntries(opposite)),
                diagnostic(waiting).until(MapConditions.hasEntry(opposite)), diagnostic(waiting).until(MapConditions.hasNoEntry(check)))) {
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Because: entry requirement"), failure.message());
            assertEquals("entry requirement", result.attempts().getLast().last().condition().explanation());
        }
    }

    @Test
    void sequenceFailureIdentifiesTheStageAndItsReasonInBothTerminals() {
        var time = new FakeTime(0);
        var waiting = await(() -> "created").usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        var first = equalTo("created").because("payment must be created");
        var second = equalTo("paid").because("payment must be completed");

        var sequence = captured(first, second).because("checkout lifecycle");
        var result = diagnostic(waiting).until(sequence);
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        var thrown = assertThrows(AwaitAssertionError.class, () -> waiting.until(sequence));

        assertEquals(thrown.getMessage(), failure.message());
        assertTrue(failure.message().contains("Stage: 2 of 2"), failure.message());
        assertTrue(failure.message().contains("Expected: paid"), failure.message());
        assertTrue(failure.message().contains("Because: payment must be completed"), failure.message());
        assertTrue(failure.message().contains("Because: checkout lifecycle"), failure.message());
        assertTrue(failure.message().contains("Mismatch: value was not equal"), failure.message());
        assertFalse(failure.message().contains("payment must be created"), failure.message());
        assertEquals(second.explanation(), result.attempts().getLast().last().condition().explanation());
    }

    @Test
    void composedChecksInheritReasonsAndAllowAnOuterOverride() {
        var nested = equalTo("ready").because("inner reason");
        for (boolean override : List.of(false, true)) {
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
            for (boolean present : List.of(false, true)) {
                Optional<String> value = present ? Optional.of("pending") : Optional.empty();
                Map<String, String> entries = present ? Map.of("id", "pending") : Map.of();
                List<String> values = present ? List.of("pending") : List.of();
                var time = new FakeTime(0);
                var results = List.of(
                        tryAwait(() -> value).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(optional),
                        tryAwait(() -> entries).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(mapped),
                        tryAwait(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(selected),
                        tryAwait(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(quantified));
                for (var result : results) {
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                    assertTrue(failure.message().contains("Because: " + expected), failure.message());
                    assertEquals(expected, result.attempts().getLast().last().condition().explanation());
                    if (override) assertFalse(failure.message().contains("inner reason"), failure.message());
                }
            }
        }
        assertEquals("inner reason", nested.explanation());
    }
}
