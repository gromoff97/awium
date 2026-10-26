package io.github.gromoff97.awium;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.MapConditions.awaitMap;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MapIterableSourceTest {

    private static final User ACTIVE = new User(1, true);

    @Test
    void mapsThatAlsoIterateValuesKeepMapSelectionTypes() {
        var both = new MapWithUsers();
        var time = new FakeTime(0);
        var waiting = awaitMap(() -> both).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(10, waiting.until(MapConditions.singleEntry).getValue().intValue());
        assertEquals(10, waiting.until(MapConditions.valueFor("key")).intValue());
        assertEquals(10, waiting.until(MapConditions.singleEntry((key, value) -> true).because("map entry")).getValue().intValue());
        assertEquals(10, waiting.until(MapConditions.valueFor("key", java.util.Objects::nonNull)).intValue());
        AwaitResult<MapWithUsers, Map.Entry<String, Integer>> captured = waiting.tryUntil(MapConditions.singleEntry);
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var entry) -> assertEquals(10, entry.getValue().intValue());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
    }

    private record User(int id, boolean active) {
        boolean inactive() { return !active; }
    }

    private static final class MapWithUsers extends AbstractMap<String, Integer> implements Iterable<User> {
        @Override public Set<Map.Entry<String, Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
        @Override public Iterator<User> iterator() { return List.of(ACTIVE).iterator(); }
    }

}
