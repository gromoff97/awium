package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
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
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

class NestedQuantifierTest {

    private final FakeTime time = new FakeTime(0);

    private record User(int id, boolean active) {}

    private static final User ACTIVE = new User(1, true);
    private static final User PENDING = new User(2, false);

    @Test
    void selectionsInsideQuantifiersCheckEachValueAndKeepTheOriginalCollection() {
        var ready = new ArrayList<>(List.of(Optional.of(ACTIVE)));
        var mixed = new ArrayList<>(List.of(Optional.<User>empty(), Optional.of(ACTIVE)));
        var empty = new ArrayList<>(List.of(Optional.<User>empty()));

        ArrayList<Optional<User>> allReady = await(() -> ready)
                .until(all(OptionalConditions.present).because("each user is available"));
        ArrayList<Optional<User>> someReady = tryAwait(() -> mixed)
                .until(any(OptionalConditions.present)).orThrow();
        ArrayList<Optional<User>> noneReady = await(() -> empty)
                .until(none(OptionalConditions.present));

        assertSame(ready, allReady);
        assertSame(mixed, someReady);
        assertSame(empty, noneReady);
        var failed = tryAwait(() -> mixed).usingTime(time, time).upTo(ofNanos(1))
                .until(all(OptionalConditions.present).because("each user is available"));
        assertEquals(AwaitFailure.Reason.TIMEOUT,
                failure(failed).reason());
        assertSame(mixed, observation(attempts(failed).getFirst()));
        assertEquals("each user is available", failed.conditions().getFirst().condition().explanation());
    }

    @Test
    void mapQuantifiersAcceptSelectionsWithoutReturningTheirExtractedValues() {
        var values = new LinkedHashMap<Optional<User>, List<User>>();
        values.put(Optional.of(ACTIVE), List.of(ACTIVE));

        LinkedHashMap<Optional<User>, List<User>> checkedKeys = await(() -> values)
                .until(allKeys(OptionalConditions.present).because("keys are available"));
        LinkedHashMap<Optional<User>, List<User>> checkedValues = tryAwait(() -> values)
                .until(allValues(singleElement).because("one user per key")).orThrow();
        assertSame(values, checkedKeys);
        assertSame(values, checkedValues);
        assertSame(values, await(() -> values).until(anyKey(OptionalConditions.present)));
        assertSame(values, await(() -> values).until(anyValue(singleElement)));
        assertSame(values, await(() -> values).until(noKey(OptionalConditions.absent)));
        assertSame(values, await(() -> values).until(noValue(firstElement(instanceOf(String.class)))));

        var indexed = Map.of("group", Map.of("user", ACTIVE));
        assertSame(indexed, await(() -> indexed).until(allValues(valueFor("user"))));
    }

    @Test
    void allAnyAndNoneAcceptConditionsAndPreserveTheConcreteSource() {
        var active = new ArrayList<>(List.of(ACTIVE));
        var mixed = new ArrayList<>(List.of(PENDING, ACTIVE));
        var pending = new ArrayList<>(List.of(PENDING));
        var check = matches(User::active);

        ArrayList<User> allUsers = await(() -> active).until(all(check));
        ArrayList<User> someUsers = await(() -> mixed).until(any(check));
        ArrayList<User> noUsers = await(() -> pending).until(none(check));
        assertSame(active, allUsers);
        assertSame(mixed, someUsers);
        assertSame(pending, noUsers);
        assertSame(active, await(() -> active).until(all(equalTo(ACTIVE))));
        assertSame(mixed, await(() -> mixed).until(any(equalTo(ACTIVE))));
        assertSame(pending, await(() -> pending).until(none(equalTo(ACTIVE))));

        List<Object> heterogeneous = List.of("skip", ACTIVE);
        assertSame(heterogeneous, await(() -> heterogeneous).until(any(instanceOf(User.class))));
        assertSame(active, await(() -> active).until(all(instanceOf(User.class))));
        assertSame(pending, await(() -> pending).until(none(instanceOf(String.class))));
        assertSame(mixed, tryAwait(() -> mixed).until(any(check)).orThrow());
    }

    @Test
    void mapKeysValuesAndEntriesAcceptTheSameConditionFamilies() {
        var source = new LinkedHashMap<String, User>();
        source.put("user", ACTIVE);
        var key = matches(String::isBlank);
        var value = matches(User::active);
        var entry = matches((Map.Entry<String, User> actual) -> actual.getValue().active());

        LinkedHashMap<String, User> observed = await(() -> source).until(allValues(value));
        assertSame(source, observed);
        assertSame(source, await(() -> source).until(anyValue(value)));
        assertSame(source, await(() -> source).until(noValue(equalTo(PENDING))));
        assertSame(source, await(() -> source).until(allKeys(equalTo("user"))));
        assertSame(source, await(() -> source).until(anyKey(instanceOf(String.class))));
        assertSame(source, await(() -> source).until(noKey(key)));
        assertSame(source, await(() -> source).until(allEntries(entry)));
        assertSame(source, await(() -> source).until(hasEntry(equalTo(Map.entry("user", ACTIVE)))));
        assertSame(source, await(() -> source).until(hasNoEntry(instanceOf(String.class))));

        Map<String, Number> numbers = Map.of("count", 1);
        assertSame(numbers, await(() -> numbers).until(allEntries(equalTo(Map.entry("count", 1)))));
        assertSame(numbers, await(() -> numbers).until(allValues(equalTo(1))));
    }

    @Test
    void quantifierFamiliesRejectMismatchesAndNullCollections() {
        List<User> source = List.of(ACTIVE);
        List<Condition.PreservingCondition<? super List<User>>> checks = List.of(
                all(equalTo(PENDING)), all(instanceOf(String.class)), all(matches(user -> !user.active())),
                any(equalTo(PENDING)), any(instanceOf(String.class)), any(matches(user -> !user.active())),
                none(equalTo(ACTIVE)), none(instanceOf(User.class)), none(matches(User::active)));
        for (var check : checks) {
            for (var actual : java.util.Arrays.asList(source, null)) {
                var time = new FakeTime(0);
                var result = tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(check);
                var failure = failure(result);
                assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
                assertNull(failure.cause());
                if (actual == null) assertThat(failure.message()).contains("collection was null");
            }
        }
    }

    @Test
    void everyMapQuantifierFamilyHandlesMatchesMismatchesAndNullMaps() {
        Map<String, User> ready = new ProbeContainers.EntryMap<>(List.of(new UserEntry()));
        Map<String, User> pending = new ProbeContainers.EntryMap<>(List.of(new java.util.AbstractMap.SimpleImmutableEntry<>(null, null)));
        var key = matches(NestedQuantifierTest::readyKey);
        var value = matches(NestedQuantifierTest::readyUser);
        var entry = matches(NestedQuantifierTest::readyEntry);
        var expectedEntry = equalTo(Map.entry("user", ACTIVE));

        assertMapChecks(ready, pending, List.of(
                allKeys(key), allKeys(equalTo("user")), allKeys(instanceOf(String.class)),
                anyKey(key), anyKey(equalTo("user")), anyKey(instanceOf(String.class)),
                allValues(value), allValues(equalTo(ACTIVE)), allValues(instanceOf(User.class)),
                anyValue(value), anyValue(equalTo(ACTIVE)), anyValue(instanceOf(User.class)),
                allEntries(entry), allEntries(expectedEntry), allEntries(instanceOf(UserEntry.class)),
                hasEntry(entry), hasEntry(expectedEntry), hasEntry(instanceOf(UserEntry.class))));
        assertMapChecks(pending, ready, List.of(
                noKey(key), noKey(equalTo("user")), noKey(instanceOf(String.class)),
                noValue(value), noValue(equalTo(ACTIVE)), noValue(instanceOf(User.class)),
                hasNoEntry(entry), hasNoEntry(expectedEntry), hasNoEntry(instanceOf(UserEntry.class))));
    }

    private static void assertMapChecks(Map<String, User> successful, Map<String, User> unsuccessful,
            List<Condition.PreservingCondition<? super Map<String, User>>> checks) {
        for (var check : checks) {
            for (var source : java.util.Arrays.asList(successful, unsuccessful, null)) {
                var time = new FakeTime(0);
                var result = tryAwait(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(check);
                if (source == successful) {
                    assertSame(source, result.orThrow());
                } else {
                    var failure = failure(result);
                    assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
                    assertNull(failure.cause());
                    if (source == null) assertThat(failure.message()).contains("map was null");
                }
            }
        }
    }

    private static boolean readyKey(String key) {
        return "user".equals(key);
    }

    private static boolean readyUser(User user) {
        return ACTIVE.equals(user);
    }

    private static boolean readyEntry(Map.Entry<String, User> entry) {
        return readyKey(entry.getKey()) && readyUser(entry.getValue());
    }

    private static final class UserEntry extends java.util.AbstractMap.SimpleImmutableEntry<String, User> {
        UserEntry() { super("user", ACTIVE); }
    }

    @Test
    void shortCircuitingStopsBeforeAnotherCallbackAndEmptyInputsStayLazy() {
        int[] calls = {0};
        var check = matches((User user) -> { calls[0]++; return user.active(); });
        var source = List.of(ACTIVE, PENDING);
        assertSame(source, await(() -> source).until(any(check)));
        assertEquals(1, calls[0]);
        var failed = tryAwait(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(none(check));
        assertInstanceOf(AwaitResult.Failed.class, failed);
        assertEquals(2, calls[0]);
        var reversed = List.of(PENDING, ACTIVE);
        tryAwait(() -> reversed).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(all(check));
        assertEquals(3, calls[0]);

        var forbidden = conditionPerWait("must stay lazy", () -> {
            throw new AssertionError("empty input created nested state");
        });
        List<User> empty = List.of();
        assertSame(empty, await(() -> empty).until(all(forbidden)));
        assertSame(empty, await(() -> empty).until(none(forbidden)));
        var emptyResult = tryAwait(() -> empty).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(any(forbidden));
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure(emptyResult).reason());
    }

    @Test
    void nestedStateIsFreshForEveryWaitAndIndependentOrderedStage() {
        int[] factories = {0};
        var check = conditionPerWait("second check", () -> {
            factories[0]++;
            int[] calls = {0};
            return (User user) -> ++calls[0] >= 2 ? satisfied() : unsatisfied();
        });
        var source = List.of(ACTIVE, ACTIVE);
        var waiting = await(() -> source).usingTime(time, time).every(ofNanos(1));
        assertSame(source, waiting.until(any(check)));
        assertSame(source, waiting.until(any(check)));
        assertEquals(2, factories[0]);
        assertEquals(List.of(source, source), waiting.until(captured(any(check), any(check))));
        assertEquals(4, factories[0]);
    }

    @Test
    void nestedMismatchKeepsCauseAndReference() {
        var cause = new IOException("not ready");
        var check = condition("ready", (User user) -> unsatisfied("pending state", cause));
        var result = tryAwait(() -> List.of(PENDING)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                .until(all(check));
        var failure = failure(result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("pending state");

        Map<String, Object> missing = Map.of();
        var nested = valueFor("user", instanceOf(User.class));
        var missingResult = tryAwait(() -> List.of(missing)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                .until(any(nested));
        assertThat(failure(missingResult).message()).contains("Key: \"user\"");
    }

    @Test
    void anyThrownFailureAndAnInterruptStopBeforeTheNextValue() {
        for (Throwable cause : List.of(new IOException("checked"), new AssertionError("assertion"), new Throwable("failure"))) {
            int[] calls = {0};
            var check = matches((User user) -> { calls[0]++; throw cause; });
            var result = tryAwait(() -> List.of(ACTIVE, PENDING)).until(any(check));
            assertSame(cause, failure(result).cause());
            assertEquals(1, calls[0]);
        }
        int[] calls = {0};
        try {
            var check = matches((User user) -> { calls[0]++; currentThread().interrupt(); return false; });
            var result = tryAwait(() -> List.of(ACTIVE, PENDING)).until(any(check));
            assertEquals(AwaitFailure.Reason.INTERRUPTED, failure(result).reason());
            assertEquals(1, calls[0]);
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }
}
