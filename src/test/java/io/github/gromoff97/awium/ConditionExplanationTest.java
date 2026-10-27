package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
                valueFor("id"), instanceOf(User.class))) {
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
        Condition.SourceCondition<User, Condition.Reference<?>> check = matches(User::active);
        User direct = await(() -> user).until(check.because("active user required"));

        assertSame(users, checked);
        assertSame(users, referenced);
        for (User result : List.of(selected, only, found, mapped, narrowed, direct)) {
            assertSame(user, result);
        }
    }

    @Test
    void sequenceFailureIdentifiesTheStageAndItsReasonInBothTerminals() {
        var time = new FakeTime(0);
        var waiting = await(() -> "created").usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        var first = equalTo("created").because("payment must be created");
        var second = equalTo("paid").because("payment must be completed");

        var result = waiting.tryUntil(first, second);
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        var thrown = assertThrows(AwaitAssertionError.class, () -> waiting.until(first, second));

        assertEquals(thrown.getMessage(), failure.message());
        assertTrue(failure.message().contains("Stage: 2 of 2"), failure.message());
        assertTrue(failure.message().contains("Expected: paid"), failure.message());
        assertTrue(failure.message().contains("Because: payment must be completed"), failure.message());
        assertTrue(failure.message().contains("Mismatch: value was not equal"), failure.message());
        assertFalse(failure.message().contains("payment must be created"), failure.message());
        assertEquals(second.explanation(), result.attempts().getLast().progress().activeCondition().explanation());
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
                        await(() -> value).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(optional),
                        await(() -> entries).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(mapped),
                        await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(selected),
                        await(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(quantified));
                for (var result : results) {
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                    assertTrue(failure.message().contains("Because: " + expected), failure.message());
                    assertEquals(expected, result.attempts().getLast().condition().explanation());
                    if (override) assertFalse(failure.message().contains("inner reason"), failure.message());
                }
            }
        }
        assertEquals("inner reason", nested.explanation());
    }
}
