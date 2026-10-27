package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class EntryConditionInputTest {
    private record User(boolean active) {}

    @Test
    void directEntryPredicatesInferTypesAcrossTerminalsAndSequences() {
        var actual = new LinkedHashMap<>(Map.of("user", new User(true)));
        Await.MapSource<LinkedHashMap<String, User>> source = () -> actual;
        var time = new FakeTime(0);
        var waiting = await(source).usingTime(time, time).every(ofNanos(1));

        LinkedHashMap<String, User> all = waiting.until(allEntries(entry -> entry.getValue().active()));
        Map.Entry<String, User> single = waiting.until(singleEntry(entry -> entry.getValue().active()));
        Map.Entry<String, User> keyed = waiting.until(entryFor("user", entry -> entry.getValue().active()));
        Map.Entry<String, User> keyedPair = waiting.until(entryFor("user", (key, user) -> !key.isBlank() && user.active()));
        LinkedHashMap<String, User> explained = waiting.until(allEntries(
                (Map.Entry<String, User> entry) -> entry.getValue().active()).because("all users must be active"));
        AwaitResult<LinkedHashMap<String, User>, LinkedHashMap<String, User>> allResult =
                waiting.tryUntil(allEntries(entry -> !entry.getKey().isBlank() && entry.getValue().active()));
        AwaitResult<LinkedHashMap<String, User>, Map.Entry<String, User>> singleResult =
                waiting.tryUntil(singleEntry(entry -> !entry.getKey().isBlank() && entry.getValue().active()));
        AwaitResult<LinkedHashMap<String, User>, Map.Entry<String, User>> keyedResult =
                waiting.tryUntil(entryFor("user", entry -> !entry.getKey().isBlank() && entry.getValue().active()));
        List<LinkedHashMap<String, User>> maps = waiting.until(
                allEntries(entry -> entry.getValue().active()), allEntries(entry -> entry.getValue().active()));
        List<Map.Entry<String, User>> entries = waiting.until(
                singleEntry(entry -> entry.getValue().active()), singleEntry(entry -> entry.getValue().active()));
        List<Map.Entry<String, User>> keyedEntries = waiting.until(
                entryFor("user", entry -> entry.getValue().active()), entryFor("user", entry -> entry.getValue().active()));

        assertSame(actual, all);
        assertSame(actual, explained);
        assertSame(actual.entrySet().iterator().next(), single);
        assertSame(single, keyed);
        assertSame(single, keyedPair);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, allResult).result());
        assertSame(single, assertInstanceOf(AwaitResult.Satisfied.class, singleResult).result());
        assertSame(single, assertInstanceOf(AwaitResult.Satisfied.class, keyedResult).result());
        assertEquals(List.of(actual, actual), maps);
        assertEquals(List.of(single, single), entries);
        assertEquals(List.of(single, single), keyedEntries);
    }

    @Test
    void exactEntryConditionsCanMutateTheSourceAndKeepItsEntryAndValueTypes() {
        var user = new User(true);
        var source = new LinkedHashMap<String, User>();
        source.put("user", new User(false));
        var seen = new ArrayList<Map.Entry<String, User>>();
        var check = singleEntry((Map.Entry<String, User> entry) -> {
            seen.add(entry);
            entry.setValue(user);
            return entry.getValue().active();
        }).because("the sole user must be active");
        Map.Entry<String, User> selected = await(() -> source).until(check);
        assertSame(seen.getFirst(), selected);
        assertSame(user, source.get("user"));
        assertTrue(selected.getValue().active());
        Map.Entry<String, User> keyed = await(() -> source).until(entryFor("user", (Map.Entry<String, User> entry) -> {
            entry.setValue(user);
            return entry.getValue().active();
        }).because("the selected user must be active"));
        assertSame(selected, keyed);
        AwaitResult<LinkedHashMap<String, User>, Map.Entry<String, User>> result = await(() -> source).tryUntil(check);
        switch (result) {
            case AwaitResult.Satisfied(var attempts, var entry) -> {
                assertSame(seen.getLast(), entry);
                assertSame(user, entry.getValue());
                assertTrue(entry.getValue().active());
            }
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
    }

    @Test
    void readonlyEntryConditionsStillAcceptCovariantSourcesAfterBecause() {
        Map<? extends String, ? extends User> source = Map.of("user", new User(true));
        var selected = await(() -> source).until(singleEntry(
                (Map.Entry<? extends String, ? extends User> entry) -> entry.getValue().active()).because("user must be active"));
        var all = await(() -> source).until(allEntries(entry -> entry.getValue().active()));
        Map<String, ? extends User> keyedSource = Map.of("user", new User(true));
        var keyed = await(() -> keyedSource).until(entryFor("user",
                (Map.Entry<? extends String, ? extends User> entry) -> entry.getValue().active()).because("user must be active"));
        assertEquals("user", selected.getKey());
        assertTrue(selected.getValue().active());
        assertSame(source, all);
        assertTrue(all.get("user").active());
        assertEquals(selected, keyed);
        assertTrue(keyed.getValue().active());
    }

    @Test
    void exactEntryFailuresKeepTheirCauseAndStopTheWait() {
        var source = new LinkedHashMap<String, User>();
        source.put("first", new User(true));
        var cause = new IOException("entry failed");
        int[] calls = {0};
        CheckedPredicate<Map.Entry<String, User>> check = entry -> {
            calls[0]++;
            throw cause;
        };
        List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                () -> await(() -> source).tryUntil(singleEntry(check).because("entry must be readable")),
                () -> await(() -> source).tryUntil(entryFor("first", check).because("entry must be readable")),
                () -> await(() -> source).tryUntil(allEntries(check).because("entry must be readable")));
        for (var wait : waits) {
            calls[0] = 0;
            var result = wait.get();
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertSame(cause, failure.cause());
            assertTrue(failure.message().contains("Because: entry must be readable"));
            assertEquals(1, calls[0]);
            assertEquals(1, result.totalAttempts());
        }
    }
}
