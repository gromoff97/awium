package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Source.CollectionSource;
import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import io.github.gromoff97.awium.Source.OptionalSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PositiveFluentMatrixTest {

    private static final Duration EVERY = ofMillis(1);
    private static final Duration UP_TO = ofSeconds(1);

    @Test
    void objectFacadeExecutesEveryValidConfigurationPath() {
        var pollingTime = new FakeTime(0);
        var actual = new Object();
        Source<Object> source = () -> actual;

        assertAllSame(actual,
                await(source).usingTime(pollingTime, pollingTime).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).until(isNotNull.because("object every")),
                await(source).usingTime(pollingTime, pollingTime).upTo(UP_TO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).usingTime(pollingTime, pollingTime).upTo(UP_TO).persisting(ZERO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull.because("object all")),
                await(source).usingTime(pollingTime, pollingTime).persisting(ZERO).upTo(UP_TO).every(EVERY).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull));

        Condition<Object, Object> selecting = condition(
                "select actual", ConditionResult::satisfied);
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting.because("selected object")));
        Void nil = await((Source<Object>) () -> null).usingTime(pollingTime, pollingTime).until(isNull);
        assertSame(null, nil);
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var value = new String("value");
        OptionalSource<String> source = () -> Optional.of(value);

        String selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(present.because("optional full chain"));
        Void absentValue = await((OptionalSource<String>) Optional::empty).usingTime(pollingTime, pollingTime).until(absent);

        assertSame(value, selected);
        assertSame(null, absentValue);
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of("value"));
        CollectionSource<ArrayList<String>> source = () -> actual;

        Condition.PreservingCondition<Collection<?>> collectionCondition = nonEmpty;
        Condition.PreservingCondition<Collection<?>> explained =
                collectionCondition.because("collection full chain");
        ArrayList<String> raw = await(source).usingTime(pollingTime, pollingTime).until(collectionCondition);
        ArrayList<String> selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(explained);

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void mapFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> raw = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.nonEmpty);
        LinkedHashMap<String, String> selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(MapConditions.nonEmpty.because("map full chain"));

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void wildcardViewsPreserveContainersAndSelectOriginalElementsOncePerAttempt() {
        var time = new FakeTime(0);
        Number element = Integer.valueOf(1000);
        List<? extends Number> values = new ArrayList<>(List.of(element));
        var entries = new LinkedHashMap<Number, CharSequence>();
        entries.put(element, new StringBuilder("ready"));
        Map<? extends Number, ? extends CharSequence> map = entries;
        var entry = entries.entrySet().iterator().next();
        int[] collectionCalls = {0};
        int[] mapCalls = {0};
        var collectionSource = new CollectionViewSource<Number, List<? extends Number>>(() -> {
            collectionCalls[0]++;
            return values;
        });
        var mapSource = new MapViewSource<Number, CharSequence,
                Map<? extends Number, ? extends CharSequence>>(() -> {
                    mapCalls[0]++;
                    return map;
                });

        assertSame(values, await(collectionSource).usingTime(time, time).until(nonEmpty));
        assertSame(element, await(collectionSource).usingTime(time, time).until(single));
        assertSame(element, assertInstanceOf(AwaitResult.Satisfied.class,
                await(collectionSource).usingTime(time, time).tryUntil(single)).result());
        assertSame(map, await(mapSource).usingTime(time, time).until(MapConditions.nonEmpty));
        assertSame(entry, await(mapSource).usingTime(time, time).until(MapConditions.singleEntry));
        assertSame(entry, assertInstanceOf(AwaitResult.Satisfied.class,
                await(mapSource).usingTime(time, time).tryUntil(MapConditions.singleEntry)).result());
        assertEquals(3, collectionCalls[0]);
        assertEquals(3, mapCalls[0]);
    }

    private static void assertAllSame(Object expected, Object... actuals) {
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
