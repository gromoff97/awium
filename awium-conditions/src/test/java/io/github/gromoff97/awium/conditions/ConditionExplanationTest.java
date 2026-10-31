package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.WaitCompletion;
import io.github.gromoff97.awium.execution.WaitTiming;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
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
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.function.CheckedPredicate.not;
import static java.time.Duration.ofNanos;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.mock;

class ConditionExplanationTest {

    private record User(boolean active) {}

    @ParameterizedTest
    @MethodSource("captures")
    void capturedReasonsAreIndependentCopies(AwaitCondition original) {
        var first = original.because("first reason");
        var second = first.because("second reason");

        assertNotSame(original, first);
        assertNotSame(first, second);
        for (var family : List.of(Condition.class, PreservingCondition.class, AwaitCondition.ComparisonCondition.class,
                AwaitCondition.SelectedCondition.class, AwaitCondition.MapValueCondition.class, AwaitCondition.CollectingCondition.class)) {
            assertEquals(family.isInstance(original), family.isInstance(second), family.getSimpleName());
        }
        assertNull(original.explanation());
        assertEquals("first reason", first.explanation());
        assertEquals("second reason", second.explanation());
        assertEquals(original.description(), second.description());
        assertEquals(original.references(), second.references());
    }

    @ParameterizedTest
    @MethodSource("captures")
    void reasonsCannotBeNull(AwaitCondition original) {
        assertThrows(NullPointerException.class, () -> original.because(null));
    }

    @ParameterizedTest
    @MethodSource("captures")
    void reasonsCannotBeBlank(AwaitCondition original) {
        assertThrows(IllegalArgumentException.class, () -> original.because(" \n\t"));
    }

    private static List<Named<AwaitCondition>> captures() {
        return List.of(named("captured preserving", captured(isNotNull)), named("captured comparison", captured(equalTo("ready"))),
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
        var waiting = await(() -> actual).every(ofNanos(1));

        List<LinkedHashMap<String, Integer>> checked = List.of(
                waiting.until(allEntries(check)),
                waiting.until(hasEntry(check)),
                waiting.until(hasNoEntry(opposite)));
        checked.forEach(value -> assertSame(actual, value));

        for (var rejected : List.of(allEntries(opposite), hasEntry(opposite), hasNoEntry(check))) {
            rejection(rejected, actual);
            assertEquals("entry requirement", rejected.explanation());
        }
    }

    @Test
    void sequenceFailureIdentifiesTheStageAndItsReason() {
        var first = equalTo("created").because("payment must be created");
        var second = equalTo("paid").because("payment must be completed");
        var sequence = captured(first, second).because("checkout lifecycle");
        var run = ConditionExecution.<String, List<String>>start(sequence, true);
        probe(run, "created");
        run.record(new Attempt.SatisfiedObservation<>("created"));
        var rejected = assertInstanceOf(ConditionResult.Unsatisfied.class, probe(run, "created"));
        var attempt = new Attempt.UnsatisfiedObservation<>("created", rejected.mismatch(), rejected.cause());
        run.record(attempt);
        var result = run.finish(new WaitCompletion.Timeout(new ExecutionStep<>(false, ACQUISITION, 0, attempt)), mock(WaitTiming.class));
        var failure = failure(result);
        assertThat(failure.message()).contains("stage 2/2", "Expected: \"paid\"",
                "Because: payment must be completed", "Because: checkout lifecycle",
                "Mismatch: value was not equal").doesNotContain("payment must be created");
        assertSame(second, result.conditions().getLast().condition());
    }

    @TestFactory
    List<DynamicTest> composedChecksInheritReasonsAndAllowAnOuterOverride() {
        var tests = new ArrayList<DynamicTest>();
        for (var value : List.of(Optional.<String>empty(), Optional.of("pending"))) {
            tests.add(dynamicTest("optional / " + value, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = hasValue(nested);
                assertInheritedAndOverriddenReasons(nested, check, value);
            }));
        }
        for (var entries : List.of(Map.<String, String>of(), Map.of("id", "pending"))) {
            tests.add(dynamicTest("map / " + entries, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = valueFor("id", nested);
                assertInheritedAndOverriddenReasons(nested, check, entries);
            }));
        }
        for (var values : List.of(List.<String>of(), List.of("pending"))) {
            tests.add(dynamicTest("single element / " + values, () -> {
                var nested = equalTo("ready").because("inner reason");
                var check = singleElement(nested);
                assertInheritedAndOverriddenReasons(nested, check, values);
            }));
        }
        tests.add(dynamicTest("all elements", () -> {
            var nested = equalTo("ready").because("inner reason");
            var check = all(nested);
                assertInheritedAndOverriddenReasons(nested, check, List.of("pending"));
        }));
        return tests;
    }

    private static void assertInheritedAndOverriddenReasons(AwaitCondition nested, AwaitCondition inherited, Object actual) {
        var overridden = inherited.because("outer reason");
        rejection(inherited, actual);
        rejection(overridden, actual);
        assertEquals("inner reason", inherited.explanation());
        assertEquals("outer reason", overridden.explanation());
        assertEquals("inner reason", nested.explanation());
    }
}
