package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.await;
import static io.github.gromoff97.awium.OptionalConditions.await;
import static io.github.gromoff97.awium.MapConditions.await;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static org.junit.jupiter.api.Assertions.*;

class EntryConditionInputTest {
    private record User(boolean active) {}

    @Test
    void exactEntryConditionsCanMutateTheSourceAndKeepItsEntryAndValueTypes() {
        var user = new User(true);
        var source = new LinkedHashMap<String, User>();
        source.put("user", new User(false));
        var seen = new ArrayList<Map.Entry<String, User>>();
        var check = singleEntry(matches((Map.Entry<String, User> entry) -> {
            seen.add(entry);
            entry.setValue(user);
            return entry.getValue().active();
        }).because("inner entry")).because("source entry");
        Map.Entry<String, User> selected = await(() -> source).until(check);
        assertSame(seen.getFirst(), selected);
        assertSame(user, source.get("user"));
        assertTrue(selected.getValue().active());
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
        var selected = await(() -> source).until(singleEntry(matches(
                (Map.Entry<? extends String, ? extends User> entry) -> entry.getValue().active())
                .because("active value")).because("entry"));
        assertEquals("user", selected.getKey());
        assertTrue(selected.getValue().active());
    }

    @Test
    void exactEntryFailuresKeepTheirCauseAndStopBeforeTheNextEntry() {
        var source = new LinkedHashMap<String, User>();
        source.put("first", new User(true));
        source.put("second", new User(true));
        var cause = new IOException("entry failed");
        int[] calls = {0};
        var result = await(() -> source).tryUntil(singleEntry(matches((Map.Entry<String, User> entry) -> {
            calls[0]++;
            throw cause;
        })).because("entry failure"));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertSame(cause, failure.cause());
        assertEquals(1, calls[0]);
        assertEquals(1, result.totalAttempts());
    }
}
