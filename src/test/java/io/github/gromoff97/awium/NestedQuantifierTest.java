package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class NestedQuantifierTest {

    private record User(int id, boolean active) {}

    private static final User ACTIVE = new User(1, true);
    private static final User PENDING = new User(2, false);

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
        assertSame(mixed, assertInstanceOf(AwaitResult.Satisfied.class,
                await(() -> mixed).tryUntil(any(check))).result());
    }

    @Test
    void mapKeysValuesAndEntriesAcceptTheSameConditionFamilies() {
        var source = new LinkedHashMap<String, User>();
        source.put("user", ACTIVE);
        var key = matches(String::isBlank);
        var value = matches(User::active);
        var entry = matches((Map.Entry<String, User> actual) -> actual.getValue().active());

        LinkedHashMap<String, User> observed = await(() -> source).until(MapConditions.allValues(value));
        assertSame(source, observed);
        assertSame(source, await(() -> source).until(MapConditions.anyValue(value)));
        assertSame(source, await(() -> source).until(MapConditions.noValue(equalTo(PENDING))));
        assertSame(source, await(() -> source).until(MapConditions.allKeys(equalTo("user"))));
        assertSame(source, await(() -> source).until(MapConditions.anyKey(instanceOf(String.class))));
        assertSame(source, await(() -> source).until(MapConditions.noKey(key)));
        assertSame(source, await(() -> source).until(MapConditions.allEntries(entry)));
        assertSame(source, await(() -> source).until(MapConditions.hasEntry(equalTo(Map.entry("user", ACTIVE)))));
        assertSame(source, await(() -> source).until(MapConditions.hasNoEntry(instanceOf(String.class))));

        Map<String, Number> numbers = Map.of("count", 1);
        assertSame(numbers, await(() -> numbers).until(MapConditions.allEntries(equalTo(Map.entry("count", 1)))));
        assertSame(numbers, await(() -> numbers).until(MapConditions.allValues(equalTo(1))));
    }

    @Test
    void comparisonsAndTypeChecksCannotSkipTheObservedCollection() {
        List<User> source = List.of(ACTIVE);
        List<Condition.PreservingCondition<? super List<User>>> checks = List.of(
                all(equalTo(PENDING)), all(instanceOf(String.class)),
                none(equalTo(ACTIVE)), none(instanceOf(User.class)));
        for (var check : checks) {
            var time = new FakeTime(0);
            var result = await(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(check);
            assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
        }
    }

    @Test
    void everyMapQuantifierFamilyChecksMatchingAndMismatchingEntries() {
        Map<String, User> ready = new ProbeContainers.EntryMap<>(List.of(new UserEntry()));
        Map<String, User> pending = new ProbeContainers.EntryMap<>(List.of(new java.util.AbstractMap.SimpleImmutableEntry<>(null, null)));
        var key = matches(NestedQuantifierTest::readyKey);
        var value = matches(NestedQuantifierTest::readyUser);
        var entry = matches(NestedQuantifierTest::readyEntry);
        var expectedEntry = equalTo(Map.entry("user", ACTIVE));

        assertMapChecks(ready, pending, List.of(
                MapConditions.allKeys(key), MapConditions.allKeys(equalTo("user")), MapConditions.allKeys(instanceOf(String.class)),
                MapConditions.anyKey(key), MapConditions.anyKey(equalTo("user")), MapConditions.anyKey(instanceOf(String.class)),
                MapConditions.allValues(value), MapConditions.allValues(equalTo(ACTIVE)), MapConditions.allValues(instanceOf(User.class)),
                MapConditions.anyValue(value), MapConditions.anyValue(equalTo(ACTIVE)), MapConditions.anyValue(instanceOf(User.class)),
                MapConditions.allEntries(entry), MapConditions.allEntries(expectedEntry), MapConditions.allEntries(instanceOf(UserEntry.class)),
                MapConditions.hasEntry(entry), MapConditions.hasEntry(expectedEntry), MapConditions.hasEntry(instanceOf(UserEntry.class))));
        assertMapChecks(pending, ready, List.of(
                MapConditions.noKey(key), MapConditions.noKey(equalTo("user")), MapConditions.noKey(instanceOf(String.class)),
                MapConditions.noValue(value), MapConditions.noValue(equalTo(ACTIVE)), MapConditions.noValue(instanceOf(User.class)),
                MapConditions.hasNoEntry(entry), MapConditions.hasNoEntry(expectedEntry), MapConditions.hasNoEntry(instanceOf(UserEntry.class))));
    }

    private static void assertMapChecks(Map<String, User> successful, Map<String, User> unsuccessful,
            List<Condition.PreservingCondition<? super Map<String, User>>> checks) {
        for (var check : checks) {
            for (var source : List.of(successful, unsuccessful)) {
                var time = new FakeTime(0);
                var result = await(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(check);
                if (source == successful) {
                    assertSame(source, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
                } else {
                    assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
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
        var time = new FakeTime(0);
        var source = List.of(ACTIVE, PENDING);
        assertSame(source, await(() -> source).until(any(check)));
        assertEquals(1, calls[0]);
        var failed = await(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(none(check));
        assertInstanceOf(AwaitResult.Failed.class, failed);
        assertEquals(2, calls[0]);
        var reversed = List.of(PENDING, ACTIVE);
        await(() -> reversed).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(all(check));
        assertEquals(3, calls[0]);

        var forbidden = conditionPerWait("must stay lazy", () -> {
            throw new AssertionError("empty input created nested state");
        });
        List<User> empty = List.of();
        assertSame(empty, await(() -> empty).until(all(forbidden)));
        assertSame(empty, await(() -> empty).until(none(forbidden)));
        var emptyResult = await(() -> empty).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(any(forbidden));
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, emptyResult).failure().reason());
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
        var time = new FakeTime(0);
        var waiting = await(() -> source).usingTime(time, time).every(ofNanos(1));
        assertSame(source, waiting.until(any(check)));
        assertSame(source, waiting.until(any(check)));
        assertEquals(2, factories[0]);
        assertEquals(List.of(source, source), waiting.until(any(check), any(check)));
        assertEquals(4, factories[0]);
    }

    @Test
    void nestedMismatchKeepsCauseAndReference() {
        var cause = new IOException("not ready");
        var check = condition("ready", (User user) -> unsatisfied("pending state", cause));
        var time = new FakeTime(0);
        var result = await(() -> List.of(PENDING)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                .tryUntil(all(check));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertSame(cause, failure.cause());
        assertTrue(failure.message().contains("pending state"), failure.message());

        Map<String, Object> missing = Map.of();
        var nested = MapConditions.valueFor("user", instanceOf(User.class));
        var missingResult = await(() -> List.of(missing)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                .tryUntil(any(nested));
        var missingFailure = assertInstanceOf(AwaitResult.Failed.class, missingResult).failure();
        assertTrue(missingFailure.message().contains("Expected: user"), missingFailure.message());
    }

    @Test
    void anyThrownFailureAndAnInterruptStopBeforeTheNextValue() {
        for (Throwable cause : List.of(new IOException("checked"), new AssertionError("assertion"), new Throwable("failure"))) {
            int[] calls = {0};
            var check = matches((User user) -> { calls[0]++; throw cause; });
            var result = await(() -> List.of(ACTIVE, PENDING)).tryUntil(any(check));
            assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, result).failure().cause());
            assertEquals(1, calls[0]);
        }
        int[] calls = {0};
        try {
            var check = matches((User user) -> { calls[0]++; Thread.currentThread().interrupt(); return false; });
            var result = await(() -> List.of(ACTIVE, PENDING)).tryUntil(any(check));
            assertEquals(AwaitFailure.Reason.INTERRUPTED, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
            assertEquals(1, calls[0]);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
