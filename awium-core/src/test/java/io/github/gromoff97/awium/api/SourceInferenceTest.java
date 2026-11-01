package io.github.gromoff97.awium.api;

import static io.github.gromoff97.awium.support.TestConditions.selecting;
import static io.github.gromoff97.awium.support.TestConditions.selectingValue;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.CollectionViewSource;
import static io.github.gromoff97.awium.Await.MapViewSource;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SourceInferenceTest {
    private static final SelectedCondition<Collection<?>> ELEMENT = selecting(actual -> satisfied(actual.iterator().next()));
    private static final SelectedCondition<Optional<?>> OPTIONAL = selecting(actual -> satisfied(actual.orElseThrow()));
    private static final SelectedCondition<Map<?, ?>> ENTRY = selecting(actual -> satisfied(actual.entrySet().iterator().next()));
    private static final MapValueCondition<Map<?, ?>> VALUE = selectingValue(actual -> satisfied(actual.values().iterator().next()));

    @Test
    void callableReferencesAndLambdasKeepOptionalSelectionTypes() {
        Callable<Optional<String>> source = () -> Optional.of("ready");
        var direct = await(source::call).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(OPTIONAL);
        var lambda = await(() -> source.call()).until(OPTIONAL);
        AwaitResult<Optional<String>, String> recorded = tryAwait(source::call).until(OPTIONAL);

        assertEquals(5, direct.length());
        assertEquals(5, lambda.length());
        assertEquals(direct, recorded.orThrow());
    }

    @Test
    void callableReferencesAndLambdasKeepCollectionSelectionTypes() {
        Callable<ArrayList<String>> source = () -> new ArrayList<>(List.of("ready"));
        var direct = await(source::call).until(ELEMENT);
        var lambda = await(() -> source.call()).until(ELEMENT);
        AwaitResult<ArrayList<String>, String> recorded = tryAwait(source::call).until(ELEMENT);

        assertEquals(5, direct.length());
        assertEquals(5, lambda.length());
        assertEquals(direct, recorded.orThrow());
    }

    @Test
    void callableReferencesAndLambdasKeepMapSelectionTypes() {
        Callable<LinkedHashMap<String, Integer>> source = () -> new LinkedHashMap<>(Map.of("ready", 1));
        var direct = await(source::call).until(ENTRY);
        var lambda = await(() -> source.call()).until(VALUE);
        AwaitResult<LinkedHashMap<String, Integer>, Map.Entry<String, Integer>> recorded = tryAwait(source::call).until(ENTRY);

        assertEquals(5, direct.getKey().length());
        assertEquals(1, direct.getValue().intValue());
        assertEquals(1, lambda.intValue());
        assertEquals(direct, recorded.orThrow());
    }

    @Test
    void covariantCallablesKeepConcreteResultsAndContainers() {
        var text = new StringBuilder("ready");
        var values = new ArrayList<>(List.of("ready"));
        var entries = new LinkedHashMap<>(Map.of("ready", 1));
        Callable<? extends StringBuilder> scalar = () -> text;
        Callable<? extends Optional<String>> optional = () -> Optional.of("ready");
        Callable<? extends ArrayList<String>> collection = () -> values;
        Callable<? extends LinkedHashMap<String, Integer>> map = () -> entries;
        var original = await(scalar).until(accepted);
        var selected = await(optional::call).until(OPTIONAL);
        var element = await(collection::call).until(ELEMENT);
        var entry = await(map::call).until(ENTRY);
        AwaitResult<Optional<String>, String> recorded = tryAwait(optional::call).until(OPTIONAL);

        assertEquals(5, original.length());
        assertSame(text, original);
        assertEquals(5, selected.length());
        assertEquals(5, element.length());
        assertEquals(5, entry.getKey().length());
        assertEquals(1, entry.getValue().intValue());
        assertEquals(selected, recorded.orThrow());
        assertSame(values, await(collection::call).until(accepted));
        assertSame(entries, await(map::call).until(accepted));
    }

    @Test
    void wildcardLambdasKeepElementAndEntryBounds() {
        Number number = 1000;
        CharSequence text = new StringBuilder("ready");
        List<? extends Number> values = List.of(number);
        Map<? extends Number, ? extends CharSequence> entries = Map.of(number, text);
        var element = await(() -> values).until(ELEMENT);
        var entry = await(() -> entries).until(ENTRY);
        var original = await(() -> values).until(accepted);
        var result = tryAwait(() -> values).until(ELEMENT);

        assertEquals(1000, element.intValue());
        assertEquals(1000, entry.getKey().intValue());
        assertEquals(5, entry.getValue().length());
        assertEquals(1000, original.getFirst().intValue());
        assertSame(number, element);
        assertSame(number, entry.getKey());
        assertSame(text, entry.getValue());
        assertSame(values, original);
        assertEquals(1000, switch (result) {
            case AwaitResult.Succeeded(var history, var selected) -> selected.intValue();
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure);
        });
    }

    @Test
    void collectionViewsReadTheSourceOnceForEachTerminalCall() {
        Number element = 1000;
        List<? extends Number> values = new ArrayList<>(List.of(element));
        int[] calls = {0};
        var source = new CollectionViewSource<Number, List<? extends Number>>(() -> { calls[0]++; return values; });
        assertSame(values, await(source).until(accepted));
        List<? extends Number> compared = await(source).until(expected(values));
        assertSame(values, compared);
        var selected = await(source).until(ELEMENT);
        assertEquals(1000, selected.intValue());
        assertSame(element, selected);
        assertSame(element, tryAwait(source).until(ELEMENT).orThrow());
        assertEquals(4, calls[0]);
    }

    @Test
    void mapViewsReadTheSourceOnceForEachTerminalCall() {
        var entries = new LinkedHashMap<Number, CharSequence>();
        entries.put(1000, new StringBuilder("ready"));
        Map<? extends Number, ? extends CharSequence> map = entries;
        var entry = entries.entrySet().iterator().next();
        int[] calls = {0};
        var source = new MapViewSource<Number, CharSequence, Map<? extends Number, ? extends CharSequence>>(() -> { calls[0]++; return map; });
        assertSame(map, await(source).until(accepted));
        Map<? extends Number, ? extends CharSequence> compared = await(source).until(expected(map));
        assertSame(map, compared);
        var selected = await(source).until(ENTRY);
        assertEquals(1000, selected.getKey().intValue());
        assertEquals(5, selected.getValue().length());
        assertSame(entry, selected);
        assertSame(entry, tryAwait(source).until(ENTRY).orThrow());
        assertEquals(4, calls[0]);
    }

    @Test
    void constructorLambdasKeepTheirConcreteTypes() {
        StringBuilder text = await(() -> new StringBuilder()).until(accepted);
        ArrayList<String> values = await(() -> new ArrayList<String>()).until(accepted);
        LinkedHashMap<String, Integer> entries = await(() -> new LinkedHashMap<String, Integer>()).until(accepted);
        Optional<?> absent = await(() -> Optional.empty()).until(accepted);
        assertEquals(0, text.length());
        assertEquals(0, values.size());
        assertEquals(0, entries.size());
        assertEquals(Optional.empty(), absent);
    }
}
