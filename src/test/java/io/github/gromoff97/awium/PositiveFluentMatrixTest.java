package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.OptionalConditions.*;

import io.github.gromoff97.awium.condition.*;
import io.github.gromoff97.awium.conditions.MapConditions;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import static io.github.gromoff97.awium.await.Await.await;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
                "select actual", ConditionEvaluation::satisfied);
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

    private static void assertAllSame(Object expected, Object... actuals) {
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
