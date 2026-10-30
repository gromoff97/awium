package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.function.CheckedPredicate.not;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.allKeys;
import static io.github.gromoff97.awium.MapConditions.allValues;
import static io.github.gromoff97.awium.MapConditions.anyKey;
import static io.github.gromoff97.awium.MapConditions.anyValue;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.noKey;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static java.lang.Thread.currentThread;

class CheckedPredicateTest {

    private final FakeTime time = new FakeTime(0);

    private record User(int id, boolean ready) {
        boolean checkReady() throws IOException { return ready; }
    }

    private static final User PENDING = new User(0, false);
    private static final User FIRST = new User(1, true);
    private static final User LAST = new User(2, true);

    @Test
    void negatedCheckedReferencesKeepInputTypesAcrossRetriesAndSelections() {
        var observations = new ArrayDeque<>(List.of(FIRST, PENDING));
        var user = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                .until(matches(not(User::checkReady)));
        assertSame(PENDING, user);
        assertEquals(0, user.id());
        assertTrue(observations.isEmpty());
        assertEquals(0, await(() -> List.of(PENDING)).usingTime(time, time)
                .until(singleElement(not(User::checkReady))).id());

        AwaitResult<User, User> captured = tryAwait(() -> PENDING).usingTime(time, time)
                .until(matches(not(User::checkReady)));
        assertSame(PENDING, captured.orThrow());

        var entries = new ArrayDeque<>(List.of(Map.of("ready", FIRST), Map.of("pending", PENDING)));
        Map.Entry<String, User> entry = await(entries::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                .until(singleEntry(CheckedBiPredicate.not(CheckedPredicateTest::readyEntry)));
        assertEquals("pending", entry.getKey());
        assertEquals(0, entry.getValue().id());
        assertTrue(entries.isEmpty());
    }

    @Test
    void negationDoesNotCatchFailuresAndRejectsMissingCallbacksEagerly() {
        assertThrows(NullPointerException.class, () -> CheckedPredicate.not(null));
        assertThrows(NullPointerException.class, () -> CheckedBiPredicate.not(null));
        for (Throwable cause : List.of(new IOException("checked"), new AssertionError("assertion"),
                new Throwable("throwable"), new InterruptedException("ordinary failure"))) {
            CheckedPredicate<User> unary = not(user -> { throw cause; });
            CheckedBiPredicate<String, User> pair = CheckedBiPredicate.not((key, user) -> { throw cause; });
            var time = new FakeTime(0);
            var result = tryAwait(() -> FIRST).usingTime(time, time).until(matches(unary));
            assertSame(cause, failure(result).cause());
            var mapResult = tryAwait(() -> Map.of("ready", FIRST)).usingTime(time, time).until(hasNoEntry(pair));
            assertSame(cause, failure(mapResult).cause());
            assertEquals(1, attempts(result).size());
            assertEquals(1, attempts(mapResult).size());
            assertFalse(currentThread().isInterrupted());
        }
    }

    @Test
    void checkedMethodReferencesRetryAndKeepTypesAcrossBothTerminals() {
        var observations = new ArrayDeque<>(List.of(PENDING, FIRST));
        var waiting = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(1, waiting.until(matches(User::checkReady)).id());
        assertTrue(observations.isEmpty());
        observations.addAll(List.of(PENDING, LAST));
        AwaitResult<User, User> captured = diagnostic(waiting).until(matches(User::checkReady));
        switch (captured) {
            case AwaitResult.Satisfied(var history, var user) -> {
                assertEquals(2, user.id());
                assertEquals(2, history.getFirst().attempts().size());
            }
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
        assertTrue(observations.isEmpty());
        assertEquals(1, await(() -> FIRST).usingTime(time, time).until(matches(user -> user.checkReady())).id());
    }

    @Test
    void checkedCollectionPredicatesKeepElementAndContainerTypes() {
        List<User> values = List.of(FIRST, PENDING, LAST);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(1, waiting.until(firstElement(User::checkReady)).id());
        assertEquals(2, waiting.until(lastElement(User::checkReady)).id());
        assertEquals(1, waiting.until(elementAt(0, User::checkReady)).id());
        assertEquals(1, await(() -> List.of(FIRST)).usingTime(time, time)
                .until(singleElement(User::checkReady)).id());
        assertSame(values, waiting.until(any(User::checkReady)));

        var ready = new ArrayList<>(List.of(FIRST, LAST));
        ArrayList<User> observed = await(() -> ready).usingTime(time, time).until(all(User::checkReady));
        assertSame(ready, observed);
        var pending = List.of(PENDING);
        assertSame(pending, await(() -> pending).usingTime(time, time).until(none(User::checkReady)));
    }

    @Test
    void checkedMapPredicatesKeepTheCompleteMapType() {
        var actual = new LinkedHashMap<>(Map.of("first", FIRST, "last", LAST));
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

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
        AwaitResult<LinkedHashMap<String, User>, Map.Entry<String, User>> captured =
                diagnostic(waiting).until(singleEntry(CheckedPredicateTest::firstEntry));
        switch (captured) {
            case AwaitResult.Satisfied(var history, var selected) -> assertEquals(1, selected.getValue().id());
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
    }

    @Test
    void optionalAndKeyedValuePredicatesSkipMissingValuesAndRetryMismatches() {
        ArrayDeque<Optional<User>> optionals = new ArrayDeque<>(List.of(Optional.empty(), Optional.of(FIRST)));
        ArrayDeque<Map<String, User>> maps = new ArrayDeque<>(List.of(Map.of(), Map.of("user", PENDING), Map.of("user", FIRST)));
        int[] calls = {0};
        CheckedPredicate<User> ready = user -> { calls[0]++; return user.checkReady(); };

        assertEquals(1, await(optionals::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(4))
                .until(hasValue(ready)).id());
        assertEquals(1, calls[0]);
        assertTrue(optionals.isEmpty());
        calls[0] = 0;
        var waiting = await(maps::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(4));
        AwaitResult<Map<String, User>, User> result = diagnostic(waiting).until(valueFor("user", ready));
        switch (result) {
            case AwaitResult.Satisfied(var history, var user) -> {
                assertEquals(1, user.id());
                assertEquals(3, history.getFirst().attempts().size());
            }
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(2, calls[0]);
        assertTrue(maps.isEmpty());
    }

    @Test
    void everyPredicateFamilyStopsAtTheFirstThrowableAndRetainsIt() {
        for (Throwable cause : List.of(new IOException("checked"), new AssertionError("assertion"),
                new Throwable("throwable"), new InterruptedException("ordinary condition failure"))) {
            int[] calls = {0};
            CheckedPredicate<User> check = user -> { calls[0]++; throw cause; };
            CheckedPredicate<String> keyCheck = key -> { calls[0]++; throw cause; };
            CheckedBiPredicate<String, User> entryCheck = (key, user) -> { calls[0]++; throw cause; };
            var time = new FakeTime(0);
            var value = await(() -> FIRST).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var values = await(() -> List.of(FIRST, LAST)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var soleValue = await(() -> List.of(FIRST)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var optional = await(() -> Optional.of(FIRST)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var map = await(() -> Map.of("first", FIRST, "last", LAST))
                    .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var soleEntry = await(() -> Map.of("first", FIRST))
                    .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                    () -> diagnostic(value).until(matches(check)),
                    () -> diagnostic(optional).until(hasValue(check)),
                    () -> diagnostic(soleValue).until(singleElement(matches(check))),
                    () -> diagnostic(soleValue).until(singleElement(check)),
                    () -> diagnostic(values).until(firstElement(check)),
                    () -> diagnostic(values).until(lastElement(check)),
                    () -> diagnostic(values).until(elementAt(0, check)),
                    () -> diagnostic(values).until(all(check)),
                    () -> diagnostic(values).until(any(check)),
                    () -> diagnostic(values).until(none(check)),
                    () -> diagnostic(map).until(valueFor("first", check)),
                    () -> diagnostic(soleEntry).until(singleEntry(entryCheck)),
                    () -> diagnostic(soleEntry).until(singleEntry(entry -> check.test(entry.getValue()))),
                    () -> diagnostic(map).until(allEntries(entryCheck)),
                    () -> diagnostic(map).until(allEntries(entry -> check.test(entry.getValue()))),
                    () -> diagnostic(map).until(hasEntry(entryCheck)),
                    () -> diagnostic(map).until(hasEntry(entry -> check.test(entry.getValue()))),
                    () -> diagnostic(map).until(hasNoEntry(entryCheck)),
                    () -> diagnostic(map).until(hasNoEntry(entry -> check.test(entry.getValue()))),
                    () -> diagnostic(map).until(allKeys(keyCheck)),
                    () -> diagnostic(map).until(anyKey(keyCheck)),
                    () -> diagnostic(map).until(noKey(keyCheck)),
                    () -> diagnostic(map).until(allValues(check)),
                    () -> diagnostic(map).until(anyValue(check)),
                    () -> diagnostic(map).until(noValue(check)));

            for (var wait : waits) {
                calls[0] = 0;
                var result = wait.get();
                var failure = failure(result);
                assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
                assertSame(cause, failure.cause());
                assertEquals(1, calls[0]);
                assertEquals(1, attempts(result).size());
                assertFalse(currentThread().isInterrupted());
            }
            calls[0] = 0;
            assertSame(cause, assertThrows(AwaitExecutionException.class, () -> value.until(matches(check))).getCause());
            assertEquals(1, calls[0]);
        }
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
