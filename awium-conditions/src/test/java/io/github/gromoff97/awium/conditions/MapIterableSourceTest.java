package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.conditions.MapConditions;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MapIterableSourceTest {

    private static final User ACTIVE = new User(1, true);

    @Test
    void mapsThatAlsoIterateValuesKeepMapSelectionTypes() {
        var both = new MapWithUsers();

        var waiting = await(() -> both).every(ofNanos(1));

        assertEquals(10, waiting.until(MapConditions.singleEntry).getValue().intValue());
        assertEquals(10, waiting.until(valueFor("key")).intValue());
        assertEquals(10, waiting.until(singleEntry((key, value) -> true)).getValue().intValue());
        assertEquals(10, waiting.until(valueFor("key", java.util.Objects::nonNull)).intValue());
        AwaitResult<MapWithUsers, Map.Entry<String, Integer>> captured = tryAwait(() -> both).every(ofNanos(1)).until(MapConditions.singleEntry);
        switch (captured) {
            case AwaitResult.Succeeded(var history, var entry) -> assertEquals(10, entry.getValue().intValue());
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
    }

    private record User(int id, boolean active) {}

    private static final class MapWithUsers extends AbstractMap<String, Integer> implements Iterable<User> {
        @Override public Set<Map.Entry<String, Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
        @Override public Iterator<User> iterator() { return List.of(ACTIVE).iterator(); }
    }
}
