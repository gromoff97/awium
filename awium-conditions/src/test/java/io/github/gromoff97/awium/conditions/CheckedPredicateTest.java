package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.allKeys;
import static io.github.gromoff97.awium.conditions.MapConditions.allValues;
import static io.github.gromoff97.awium.conditions.MapConditions.anyKey;
import static io.github.gromoff97.awium.conditions.MapConditions.anyValue;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.noKey;
import static io.github.gromoff97.awium.conditions.MapConditions.noValue;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.function.CheckedPredicate.not;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static java.util.Map.Entry;
import static java.util.stream.Stream.concat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

class CheckedPredicateTest {

    private record User(int id, boolean ready) {
        boolean checkReady() throws IOException { return ready; }
    }

    private static final User PENDING = new User(0, false);
    private static final User FIRST = new User(1, true);
    private static final User LAST = new User(2, true);

    @Test
    void negatedCheckedReferencesKeepInputTypesAcrossRetriesAndSelections() {
        var observations = new ArrayDeque<>(List.of(FIRST, PENDING));
        var user = await(observations::removeFirst).every(ofNanos(1))
                .until(matches(not(User::checkReady)));
        assertSame(PENDING, user);
        assertEquals(0, user.id());
        assertTrue(observations.isEmpty());
        assertEquals(0, await(() -> List.of(PENDING))
                .until(singleElement(not(User::checkReady))).id());

        AwaitResult<User, User> captured = tryAwait(() -> PENDING)
                .until(matches(not(User::checkReady)));
        assertSame(PENDING, captured.orThrow());

        var entries = new ArrayDeque<>(List.of(Map.of("ready", FIRST), Map.of("pending", PENDING)));
        Entry<String, User> entry = await(entries::removeFirst).every(ofNanos(1))
                .until(singleEntry(CheckedBiPredicate.not(CheckedPredicateTest::readyEntry)));
        assertEquals("pending", entry.getKey());
        assertEquals(0, entry.getValue().id());
        assertTrue(entries.isEmpty());
    }

    @Test
    void checkedMethodReferencesRetryAndKeepTypesAcrossBothTerminals() {
        var observations = new ArrayDeque<>(List.of(PENDING, FIRST));
        var waiting = await(observations::removeFirst).every(ofNanos(1));

        assertEquals(1, waiting.until(matches(User::checkReady)).id());
        assertTrue(observations.isEmpty());
        observations.addAll(List.of(PENDING, LAST));
        AwaitResult<User, User> captured = tryAwait(observations::removeFirst).every(ofNanos(1)).until(matches(User::checkReady));
        switch (captured) {
            case Succeeded(var history, var user) -> {
                assertEquals(2, user.id());
                assertEquals(2, history.getFirst().attempts().size());
            }
            case Failed(var history, var failure) -> fail(failure.message());
        }
        assertTrue(observations.isEmpty());
        assertEquals(1, await(() -> FIRST).until(matches(user -> user.checkReady())).id());
    }

    @Test
    void checkedCollectionPredicatesKeepElementAndContainerTypes() {
        List<User> values = List.of(FIRST, PENDING, LAST);
        var waiting = await(() -> values).every(ofNanos(1));

        assertEquals(1, waiting.until(firstElement(User::checkReady)).id());
        assertEquals(2, waiting.until(lastElement(User::checkReady)).id());
        assertEquals(1, waiting.until(elementAt(0, User::checkReady)).id());
        assertEquals(1, await(() -> List.of(FIRST))
                .until(singleElement(User::checkReady)).id());
        assertSame(values, waiting.until(any(User::checkReady)));

        var ready = new ArrayList<>(List.of(FIRST, LAST));
        ArrayList<User> observed = await(() -> ready).until(all(User::checkReady));
        assertSame(ready, observed);
        var pending = List.of(PENDING);
        assertSame(pending, await(() -> pending).until(none(User::checkReady)));
    }

    @Test
    void checkedMapPredicatesKeepTheCompleteMapType() {
        var actual = new LinkedHashMap<>(Map.of("first", FIRST, "last", LAST));
        var waiting = await(() -> actual).every(ofNanos(1));

        List<LinkedHashMap<String, User>> observed = List.of(
                waiting.until(allEntries(CheckedPredicateTest::readyEntry)),
                waiting.until(allEntries(entry -> readyEntry(entry.getKey(), entry.getValue()))),
                waiting.until(hasEntry(CheckedPredicateTest::readyEntry)),
                waiting.until(hasEntry(entry -> readyEntry(entry.getKey(), entry.getValue()))),
                waiting.until(hasNoEntry(CheckedPredicateTest::pendingEntry)),
                waiting.until(hasNoEntry(entry -> pendingEntry(entry.getKey(), entry.getValue()))),
                waiting.until(allKeys(CheckedPredicateTest::validKey)),
                waiting.until(anyKey(CheckedPredicateTest::validKey)),
                waiting.until(noKey(CheckedPredicateTest::blankKey)),
                waiting.until(allValues(User::checkReady)),
                waiting.until(anyValue(User::checkReady)),
                waiting.until(noValue(user -> !user.checkReady())));

        observed.forEach(value -> assertSame(actual, value));
        assertEquals(1, observed.getFirst().get("first").id());
        actual.remove("last");
        var entry = waiting.until(singleEntry(CheckedPredicateTest::firstEntry));
        assertEquals(5, entry.getKey().length());
        assertEquals(1, entry.getValue().id());
        AwaitResult<LinkedHashMap<String, User>, Entry<String, User>> captured =
                tryAwait(() -> actual).every(ofNanos(1)).until(singleEntry(CheckedPredicateTest::firstEntry));
        switch (captured) {
            case Succeeded(var history, var selected) -> assertEquals(1, selected.getValue().id());
            case Failed(var history, var failure) -> fail(failure.message());
        }
    }

    @Test
    void optionalPredicatesSkipMissingValues() {
        ArrayDeque<Optional<User>> optionals = new ArrayDeque<>(List.of(Optional.empty(), Optional.of(FIRST)));
        int[] calls = {0};
        CheckedPredicate<User> ready = user -> { calls[0]++; return user.checkReady(); };

        assertEquals(1, await(optionals::removeFirst).every(ofNanos(1))
                .until(hasValue(ready)).id());
        assertEquals(1, calls[0]);
        assertTrue(optionals.isEmpty());
    }

    @Test
    void keyedValuePredicatesSkipMissingValuesAndRetryMismatches() {
        ArrayDeque<Map<String, User>> maps = new ArrayDeque<>(List.of(Map.of(), Map.of("user", PENDING), Map.of("user", FIRST)));
        int[] calls = {0};
        CheckedPredicate<User> ready = user -> { calls[0]++; return user.checkReady(); };

        AwaitResult<Map<String, User>, User> result = tryAwait(maps::removeFirst).every(ofNanos(1)).until(valueFor("user", ready));
        switch (result) {
            case Succeeded(var history, var user) -> {
                assertEquals(1, user.id());
                assertEquals(3, history.getFirst().attempts().size());
            }
            case Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(2, calls[0]);
        assertTrue(maps.isEmpty());
    }

    private static Stream<Named<Executable>> predicateFamilyFailures() {
        return Stream.of(new IOException("checked"), new AssertionError("assertion"),
                new Throwable("throwable"), new InterruptedException("ordinary condition failure"))
                .flatMap(CheckedPredicateTest::predicateFailures);
    }

    private static Stream<Named<Executable>> predicateFailures(Throwable cause) {
        int[] calls = {0};
        CheckedPredicate<User> check = user -> { calls[0]++; throw cause; };
        CheckedPredicate<String> keyCheck = key -> { calls[0]++; throw cause; };
        CheckedBiPredicate<String, User> entryCheck = (key, user) -> { calls[0]++; throw cause; };

        var value = await(() -> FIRST).every(ofNanos(1));



        var map = await(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1));

        List<Named<Supplier<AwaitResult<?, ?>>>> waits = List.of(
                named("matches", () -> tryAwait(() -> FIRST).every(ofNanos(1)).until(matches(check))),
                named("hasValue", () -> tryAwait(() -> Optional.of(FIRST)).every(ofNanos(1)).until(hasValue(check))),
                named("singleElement(matches)", () -> tryAwait(() -> List.of(FIRST)).every(ofNanos(1)).until(singleElement(matches(check)))),
                named("singleElement(predicate)", () -> tryAwait(() -> List.of(FIRST)).every(ofNanos(1)).until(singleElement(check))),
                named("firstElement", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(firstElement(check))),
                named("lastElement", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(lastElement(check))),
                named("elementAt", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(elementAt(0, check))),
                named("all", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(all(check))),
                named("any", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(any(check))),
                named("none", () -> tryAwait(() -> List.of(FIRST, LAST)).every(ofNanos(1)).until(none(check))),
                named("valueFor", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(valueFor("first", check))),
                named("singleEntry(key, value)", () -> tryAwait(() -> Map.of("first", FIRST))
                .every(ofNanos(1)).until(singleEntry(entryCheck))),
                named("singleEntry(entry)", () -> tryAwait(() -> Map.of("first", FIRST))
                .every(ofNanos(1)).until(singleEntry(entry -> check.test(entry.getValue())))),
                named("allEntries(key, value)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(allEntries(entryCheck))),
                named("allEntries(entry)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(allEntries(entry -> check.test(entry.getValue())))),
                named("hasEntry(key, value)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(hasEntry(entryCheck))),
                named("hasEntry(entry)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(hasEntry(entry -> check.test(entry.getValue())))),
                named("hasNoEntry(key, value)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(hasNoEntry(entryCheck))),
                named("hasNoEntry(entry)", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(hasNoEntry(entry -> check.test(entry.getValue())))),
                named("allKeys", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(allKeys(keyCheck))),
                named("anyKey", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(anyKey(keyCheck))),
                named("noKey", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(noKey(keyCheck))),
                named("allValues", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(allValues(check))),
                named("anyValue", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(anyValue(check))),
                named("noValue", () -> tryAwait(() -> Map.of("first", FIRST, "last", LAST))
                .every(ofNanos(1)).until(noValue(check))));
        var label = cause.getClass().getSimpleName();
        return concat(waits.stream().map(wait -> named(label + " / " + wait.getName(), () -> {
            calls[0] = 0;
            var result = wait.getPayload().get();
            var failure = failure(CONDITION_FAILED, result);
            assertSame(cause, failure.cause());
            assertEquals(1, calls[0]);
            assertEquals(1, attempts(result).size());
        })), Stream.of(named(label + " / await matches", () -> {
            calls[0] = 0;
            assertSame(cause, assertThrows(AwaitExecutionException.class, () -> value.until(matches(check))).getCause());
            assertEquals(1, calls[0]);
        })));
    }

    @ParameterizedTest
    @MethodSource("predicateFamilyFailures")
    @Execution(SAME_THREAD)
    void predicateFailureStopsTheWaitWithoutInterruptingTheCaller(Executable scenario) {
        assertAll(scenario, () -> assertFalse(currentThread().isInterrupted(), "predicate failures must not interrupt the calling thread"));
    }

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    private static boolean readyEntry(String key, User user) throws Throwable {
        return validKey(key) && user.checkReady();
    }

    private static boolean firstEntry(CharSequence key, User user) throws Throwable {
        return !key.isEmpty() && user.id() == 1 && user.checkReady();
    }

    private static boolean pendingEntry(String key, User user) throws Throwable {
        return validKey(key) && !user.checkReady();
    }

    private static boolean validKey(String key) throws Throwable { return !key.isBlank(); }

    private static boolean blankKey(String key) throws Throwable { return key.isBlank(); }
}
