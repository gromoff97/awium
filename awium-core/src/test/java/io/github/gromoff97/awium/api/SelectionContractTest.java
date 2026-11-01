package io.github.gromoff97.awium.api;

import static io.github.gromoff97.awium.support.TestConditions.selecting;
import static io.github.gromoff97.awium.support.TestConditions.selectingValue;
import io.github.gromoff97.awium.Await.CollectionSource;
import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.Await.MapViewSource;
import io.github.gromoff97.awium.Await.OptionalSource;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.support.AwaitAssertions.observation;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SelectionContractTest {
    private static final BigDecimal VALUE = new BigDecimal("42.00");
    private static final Map.Entry<String, BigDecimal> ENTRY = Map.entry("key", VALUE);
    private static final SelectedCondition<Collection<?>> ELEMENT = selecting(actual -> satisfied(VALUE));
    private static final SelectedCondition<Optional<?>> OPTIONAL = selecting(actual -> satisfied(VALUE));
    private static final SelectedCondition<Map<?, ?>> MAP_ENTRY = selecting(actual -> satisfied(ENTRY));
    private static final MapValueCondition<Map<?, ?>> MAP_VALUE = selectingValue(actual -> satisfied(VALUE));

    @Test
    void collectionSourcesKeepTheSelectedTypeAfterEverySetting() {
        var actual = new ArrayList<>(List.of(VALUE));
        CollectionSource<ArrayList<BigDecimal>> source = () -> actual;
        var selected = await(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(ELEMENT);

        assertEquals(2, selected.scale());
        assertSame(VALUE, selected);
    }

    @Test
    void recordedCollectionSelectionsKeepTheSourceAndResultTypes() {
        var actual = new ArrayList<>(List.of(VALUE));
        CollectionSource<ArrayList<BigDecimal>> source = () -> actual;
        var result = tryAwait(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(ELEMENT);

        assertEquals(2, result.orThrow().scale());
        assertSame(actual, observation(result.conditions().getFirst().attempts().firstEntry().getValue()));
    }

    @Test
    void optionalSourcesKeepTheSelectedType() {
        OptionalSource<BigDecimal> source = () -> Optional.of(VALUE);
        var selected = await(source).until(OPTIONAL);
        assertEquals(2, selected.scale());
        assertSame(VALUE, selected);
    }

    @Test
    void recordedOptionalSelectionsKeepTheSelectedType() {
        OptionalSource<BigDecimal> source = () -> Optional.of(VALUE);
        var result = tryAwait(source).until(OPTIONAL);
        assertEquals(2, result.orThrow().scale());
        assertSame(VALUE, result.orThrow());
    }

    @Test
    void mapSourcesKeepEntryTypesAfterEverySetting() {
        MapSource<LinkedHashMap<String, BigDecimal>> source = () -> new LinkedHashMap<>(Map.of("key", VALUE));
        var selected = await(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(MAP_ENTRY);
        assertEquals(2, selected.getValue().scale());
        assertSame(ENTRY, selected);
    }

    @Test
    void mapSourcesKeepValueTypesAfterEverySetting() {
        MapSource<LinkedHashMap<String, BigDecimal>> source = () -> new LinkedHashMap<>(Map.of("key", VALUE));
        var selected = await(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(MAP_VALUE);
        assertEquals(2, selected.scale());
        assertSame(VALUE, selected);
    }

    @Test
    void recordedMapSelectionsKeepEntryTypesAfterEverySetting() {
        MapSource<LinkedHashMap<String, BigDecimal>> source = () -> new LinkedHashMap<>(Map.of("key", VALUE));
        var result = tryAwait(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(MAP_ENTRY);
        assertEquals(2, result.orThrow().getValue().scale());
        assertSame(ENTRY, result.orThrow());
    }

    @Test
    void recordedMapSelectionsKeepValueTypesAfterEverySetting() {
        MapSource<LinkedHashMap<String, BigDecimal>> source = () -> new LinkedHashMap<>(Map.of("key", VALUE));
        var result = tryAwait(source).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(MAP_VALUE);
        assertEquals(2, result.orThrow().scale());
        assertSame(VALUE, result.orThrow());
    }

    @Test
    void collectionViewsRetainTheDeclaredElementBound() {
        List<? extends Number> actual = List.of(VALUE);
        var source = new CollectionViewSource<Number, List<? extends Number>>(() -> actual);
        var selected = await(source).until(ELEMENT);
        assertEquals(42, selected.intValue());
        assertSame(VALUE, selected);
    }

    @Test
    void recordedCollectionViewsRetainTheOriginalContainer() {
        List<? extends Number> actual = List.of(VALUE);
        var source = new CollectionViewSource<Number, List<? extends Number>>(() -> actual);
        var result = tryAwait(source).until(ELEMENT);
        assertEquals(42, result.orThrow().intValue());
        assertSame(actual, observation(result.conditions().getFirst().attempts().firstEntry().getValue()));
    }

    @Test
    void mapViewsRetainEntryBounds() {
        Map<? extends String, ? extends Number> actual = Map.of("key", VALUE);
        var source = new MapViewSource<String, Number, Map<? extends String, ? extends Number>>(() -> actual);
        var selected = await(source).until(MAP_ENTRY);
        assertEquals(42, selected.getValue().intValue());
        assertSame(ENTRY, selected);
    }

    @Test
    void mapViewsRetainValueBounds() {
        Map<? extends String, ? extends Number> actual = Map.of("key", VALUE);
        var source = new MapViewSource<String, Number, Map<? extends String, ? extends Number>>(() -> actual);
        var selected = await(source).until(MAP_VALUE);
        assertEquals(42, selected.intValue());
        assertSame(VALUE, selected);
    }

    @Test
    void recordedMapViewsRetainEntryBounds() {
        Map<? extends String, ? extends Number> actual = Map.of("key", VALUE);
        var source = new MapViewSource<String, Number, Map<? extends String, ? extends Number>>(() -> actual);
        var result = tryAwait(source).until(MAP_ENTRY);
        assertEquals(42, result.orThrow().getValue().intValue());
        assertSame(ENTRY, result.orThrow());
    }

    @Test
    void recordedMapViewsRetainValueBounds() {
        Map<? extends String, ? extends Number> actual = Map.of("key", VALUE);
        var source = new MapViewSource<String, Number, Map<? extends String, ? extends Number>>(() -> actual);
        var result = tryAwait(source).until(MAP_VALUE);
        assertEquals(42, result.orThrow().intValue());
        assertSame(VALUE, result.orThrow());
    }
}
