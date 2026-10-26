package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
import static io.github.gromoff97.awium.MapConditions.awaitMap;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.CheckedPredicate.not;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

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
        var time = new FakeTime(0);
        var user = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                .until(matches(not(User::checkReady)).because("not ready"));
        assertSame(PENDING, user);
        assertEquals(0, user.id());
        assertTrue(observations.isEmpty());
        assertEquals(0, awaitCollection(() -> List.of(FIRST, PENDING)).usingTime(time, time)
                .until(single(not(User::checkReady)).because("not ready")).id());

        AwaitResult<User, User> captured = await(() -> PENDING).usingTime(time, time)
                .tryUntil(matches(not(User::checkReady)).because("not ready"));
        assertSame(PENDING, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());

        Map.Entry<String, User> entry = awaitMap(() -> Map.of("pending", PENDING, "ready", FIRST)).usingTime(time, time)
                .until(MapConditions.singleEntry(CheckedBiPredicate.not(CheckedPredicateTest::readyEntry)).because("not ready"));
        assertEquals("pending", entry.getKey());
        assertEquals(0, entry.getValue().id());
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
            var result = await(() -> FIRST).usingTime(time, time).tryUntil(matches(unary));
            assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, result).failure().cause());
            var mapResult = awaitMap(() -> Map.of("ready", FIRST)).usingTime(time, time).tryUntil(MapConditions.noEntry(pair));
            assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, mapResult).failure().cause());
            assertEquals(1, result.totalAttempts());
            assertEquals(1, mapResult.totalAttempts());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void checkedMethodReferencesRetryAndKeepTypesThroughBecauseAndBothTerminals() {
        var observations = new ArrayDeque<>(List.of(PENDING, FIRST));
        var time = new FakeTime(0);
        var waiting = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        Supplier<String> forbidden = () -> { throw new AssertionError("successful check rendered its reason"); };

        assertEquals(1, waiting.until(matches(User::checkReady).because(forbidden)).id());
        assertTrue(observations.isEmpty());
        observations.addAll(List.of(PENDING, LAST));
        AwaitResult<User, User> captured = waiting.tryUntil(matches(User::checkReady).because("ready"));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var user) -> {
                assertEquals(2, user.id());
                assertEquals(2, attempts.size());
            }
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
        assertTrue(observations.isEmpty());
        assertEquals(1, await(() -> FIRST).usingTime(time, time).until(matches(user -> user.checkReady())).id());
    }

    @Test
    void checkedCollectionPredicatesKeepElementAndContainerTypes() {
        var time = new FakeTime(0);
        List<User> values = List.of(PENDING, FIRST, LAST);
        var waiting = awaitCollection(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(1, waiting.until(first(User::checkReady).because("first ready")).id());
        assertEquals(2, waiting.until(last(User::checkReady).because("last ready")).id());
        assertEquals(1, waiting.until(element(1, User::checkReady).because("indexed ready")).id());
        assertEquals(1, awaitCollection(() -> List.of(PENDING, FIRST)).usingTime(time, time)
                .until(single(User::checkReady).because("only ready")).id());
        assertSame(values, waiting.until(any(User::checkReady).because("any ready")));

        var ready = new ArrayList<>(List.of(FIRST, LAST));
        ArrayList<User> observed = awaitCollection(() -> ready).usingTime(time, time).until(all(User::checkReady));
        assertSame(ready, observed);
        var pending = List.of(PENDING);
        assertSame(pending, awaitCollection(() -> pending).usingTime(time, time).until(none(User::checkReady)));
    }

    @Test
    void checkedMapPredicatesKeepTheCompleteMapType() {
        var actual = new LinkedHashMap<>(Map.of("first", FIRST, "last", LAST));
        var time = new FakeTime(0);
        var waiting = awaitMap(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        List<LinkedHashMap<String, User>> observed = List.of(
                waiting.until(MapConditions.allEntries(CheckedPredicateTest::readyEntry).because("all entries")),
                waiting.until(MapConditions.anyEntry(CheckedPredicateTest::readyEntry).because("an entry")),
                waiting.until(MapConditions.noEntry(CheckedPredicateTest::pendingEntry).because("no pending entries")),
                waiting.until(MapConditions.allKeys(CheckedPredicateTest::validKey)),
                waiting.until(MapConditions.anyKey(CheckedPredicateTest::validKey)),
                waiting.until(MapConditions.noKey(CheckedPredicateTest::blankKey)),
                waiting.until(MapConditions.allValues(User::checkReady)),
                waiting.until(MapConditions.anyValue(User::checkReady)),
                waiting.until(MapConditions.noValue(user -> !user.checkReady())));

        observed.forEach(value -> assertSame(actual, value));
        assertEquals(1, observed.getFirst().get("first").id());
        var entry = waiting.until(MapConditions.singleEntry(CheckedPredicateTest::firstEntry).because("first user"));
        assertEquals(5, entry.getKey().length());
        assertEquals(1, entry.getValue().id());
        AwaitResult<LinkedHashMap<String, User>, Map.Entry<String, User>> captured =
                waiting.tryUntil(MapConditions.singleEntry(CheckedPredicateTest::firstEntry));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var selected) -> assertEquals(1, selected.getValue().id());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
    }

    @Test
    void optionalAndKeyedValuePredicatesSkipMissingValuesAndRetryMismatches() {
        ArrayDeque<Optional<User>> optionals = new ArrayDeque<>(List.of(Optional.empty(), Optional.of(FIRST)));
        ArrayDeque<Map<String, User>> maps = new ArrayDeque<>(List.of(Map.of(), Map.of("user", PENDING), Map.of("user", FIRST)));
        int[] calls = {0};
        CheckedPredicate<User> ready = user -> { calls[0]++; return user.checkReady(); };
        var time = new FakeTime(0);

        assertEquals(1, awaitOptional(optionals::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(4))
                .until(OptionalConditions.hasValue(ready).because("present and ready")).id());
        assertEquals(1, calls[0]);
        assertTrue(optionals.isEmpty());
        calls[0] = 0;
        var waiting = awaitMap(maps::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(4));
        AwaitResult<Map<String, User>, User> result = waiting.tryUntil(MapConditions.valueFor("user", ready).because("keyed ready"));
        switch (result) {
            case AwaitResult.Satisfied(var attempts, var user) -> {
                assertEquals(1, user.id());
                assertEquals(3, attempts.size());
            }
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
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
            var values = awaitCollection(() -> List.of(FIRST, LAST)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var optional = awaitOptional(() -> Optional.of(FIRST)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            var map = awaitMap(() -> Map.of("first", FIRST, "last", LAST))
                    .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
            List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                    () -> value.tryUntil(matches(check).because("checked")),
                    () -> optional.tryUntil(OptionalConditions.hasValue(check)),
                    () -> values.tryUntil(single(matches(check))),
                    () -> values.tryUntil(single(check)),
                    () -> values.tryUntil(first(check)),
                    () -> values.tryUntil(last(check)),
                    () -> values.tryUntil(element(0, check)),
                    () -> values.tryUntil(all(check)),
                    () -> values.tryUntil(any(check)),
                    () -> values.tryUntil(none(check)),
                    () -> map.tryUntil(MapConditions.valueFor("first", check)),
                    () -> map.tryUntil(MapConditions.singleEntry(entryCheck)),
                    () -> map.tryUntil(MapConditions.allEntries(entryCheck)),
                    () -> map.tryUntil(MapConditions.anyEntry(entryCheck)),
                    () -> map.tryUntil(MapConditions.noEntry(entryCheck)),
                    () -> map.tryUntil(MapConditions.allKeys(keyCheck)),
                    () -> map.tryUntil(MapConditions.anyKey(keyCheck)),
                    () -> map.tryUntil(MapConditions.noKey(keyCheck)),
                    () -> map.tryUntil(MapConditions.allValues(check)),
                    () -> map.tryUntil(MapConditions.anyValue(check)),
                    () -> map.tryUntil(MapConditions.noValue(check)));

            for (var wait : waits) {
                calls[0] = 0;
                var result = wait.get();
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
                assertSame(cause, failure.cause());
                assertEquals(1, calls[0]);
                assertEquals(1, result.attempts().size());
                assertFalse(Thread.currentThread().isInterrupted());
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
