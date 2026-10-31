package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.CollectionSource;
import static io.github.gromoff97.awium.Await.MapSource;
import static io.github.gromoff97.awium.Await.OptionalSource;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.function.CheckedPredicate.not;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static java.time.Duration.ofNanos;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Named.named;

class ConditionExplanationTest {

    private final FakeTime time = new FakeTime(0);

    private record User(boolean active) {}

    @ParameterizedTest
    @MethodSource("conditionDefinitions")
    void reasonsAreIndependentCopiesForEveryConditionFamily(Condition original) {
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
    }

    @ParameterizedTest
    @MethodSource("conditionDefinitions")
    void reasonsCannotBeNull(Condition original) {
        assertThrows(NullPointerException.class, () -> original.because(null));
    }

    @ParameterizedTest
    @MethodSource("conditionDefinitions")
    void reasonsCannotBeBlank(Condition original) {
        assertThrows(IllegalArgumentException.class, () -> original.because(" \n\t"));
    }

    private static List<Named<Condition>> conditionDefinitions() {
        return List.of(named("preserving", isNotNull), named("comparison", equalTo("ready")), named("optional", present),
                named("element", singleElement), named("map value", valueFor("id")), named("narrowing", instanceOf(User.class)),
                named("captured preserving", captured(isNotNull)), named("captured comparison", captured(equalTo("ready"))),
                named("captured optional", captured(present)), named("captured map value", captured(valueFor("id"))),
                named("captured narrowing", captured(instanceOf(User.class))));
    }

    @Test
    void typedLambdasAndMethodReferencesKeepTheFluentResults() {
        var user = new User(true);
        var users = new ArrayList<>(List.of(user));
        CollectionSource<ArrayList<User>> source = () -> users;
        OptionalSource<User> optional = () -> Optional.of(user);
        MapSource<Map<String, User>> map = () -> Map.of("id", user);

        ArrayList<User> checked = await(source).until(all((User value) -> value.active()).because("active users required"));
        ArrayList<User> referenced = await(source).until(all(User::active).because("active users required"));
        User selected = await(source).until(singleElement((User value) -> value.active()).because("one active user required"));
        User only = await(source).until(singleElement.because("one user required"));
        User found = await(optional).until(present.because("user must exist"));
        User mapped = await(map).until(valueFor("id", (User value) -> value.active()).because("user must be active"));
        User narrowed = await(() -> (Object) user).until(instanceOf(User.class).because("user value required"));
        PreservingCondition<User> check = matches(User::active);
        User direct = await(() -> user).until(check.because("active user required"));

        assertSame(users, checked);
        assertSame(users, referenced);
        assertThat(List.of(selected, only, found, mapped, narrowed, direct))
                .allSatisfy(result -> assertSame(user, result));
    }

    @Test
    void wildcardEntryChecksKeepTheMapTypeAndInheritedReason() {
        CheckedPredicate<Entry<String, ? extends Number>> positive = entry -> entry.getValue().intValue() > 0;
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
            var failure = failure(TIMEOUT, result);
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

    @TestFactory
    List<DynamicTest> composedChecksInheritReasonsAndAllowAnOuterOverride() {
        var tests = new ArrayList<DynamicTest>();
        for (var value : List.of(Optional.<String>empty(), Optional.of("pending"))) {
            tests.add(dynamicTest("optional / " + value, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = hasValue(nested);
                var time = new FakeTime(0);
                var waiting = tryAwait(() -> value).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
                assertInheritedAndOverriddenReasons(nested, waiting.until(check), waiting.until(check.because("outer reason")));
            }));
        }
        for (var entries : List.of(Map.<String, String>of(), Map.of("id", "pending"))) {
            tests.add(dynamicTest("map / " + entries, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = valueFor("id", nested);
                var time = new FakeTime(0);
                var waiting = tryAwait(() -> entries).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
                assertInheritedAndOverriddenReasons(nested, waiting.until(check), waiting.until(check.because("outer reason")));
            }));
        }
        for (var values : List.of(List.<String>of(), List.of("pending"))) {
            tests.add(dynamicTest("single element / " + values, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = singleElement(nested);
                var time = new FakeTime(0);
                var waiting = tryAwait(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
                assertInheritedAndOverriddenReasons(nested, waiting.until(check), waiting.until(check.because("outer reason")));
            }));
        }
        tests.add(dynamicTest("all elements", () -> {
            var nested = equalTo("ready").because("inner reason");
            var check = all(nested);
            var time = new FakeTime(0);
            var waiting = tryAwait(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            assertInheritedAndOverriddenReasons(nested, waiting.until(check), waiting.until(check.because("outer reason")));
        }));
        return tests;
    }

    private static void assertInheritedAndOverriddenReasons(Condition nested, AwaitResult<?, ?> inherited, AwaitResult<?, ?> overridden) {
        assertThat(failure(inherited).message()).contains("Because: inner reason");
        assertEquals("inner reason", inherited.conditions().getLast().condition().explanation());
        assertThat(failure(overridden).message()).contains("Because: outer reason").doesNotContain("inner reason");
        assertEquals("outer reason", overridden.conditions().getLast().condition().explanation());
        assertEquals("inner reason", nested.explanation());
    }
}
