package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.fluent.CollectionConditions.*;
import static io.github.gromoff97.awium.fluent.Conditions.*;
import static io.github.gromoff97.awium.fluent.OptionalConditions.*;

import io.github.gromoff97.awium.evaluation.*;
import io.github.gromoff97.awium.fluent.*;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import static io.github.gromoff97.awium.fluent.Await.await;
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
        var actual = new Object();
        Source<Object> source = () -> actual;

        assertAllSame(actual,
                await(source).until(isNotNull),
                await(source).every(EVERY).until(isNotNull.because("object every")),
                await(source).upTo(UP_TO).until(isNotNull),
                await(source).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).every(EVERY).upTo(UP_TO).until(isNotNull),
                await(source).every(EVERY).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).upTo(UP_TO).persisting(ZERO).until(isNotNull),
                await(source).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull.because("object all")),
                await(source).persisting(ZERO).upTo(UP_TO).every(EVERY).until(isNotNull),
                await(source).every(EVERY).upTo(UP_TO).persisting(ZERO).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull));

        Condition<Object, Object> selecting = condition(
                "select actual", ConditionEvaluation::satisfied);
        assertSame(actual, await(source).until(selecting));
        assertSame(actual, await(source).until(selecting.because("selected object")));
        Void nil = await((Source<Object>) () -> null).until(isNull);
        assertSame(null, nil);
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var value = new String("value");
        OptionalSource<String> source = () -> Optional.of(value);

        String selected = await(source).every(EVERY).upTo(UP_TO).persisting(ZERO).until(present.because("optional full chain"));
        Void absentValue = await((OptionalSource<String>) Optional::empty).until(absent);

        assertSame(value, selected);
        assertSame(null, absentValue);
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var actual = new ArrayList<>(List.of("value"));
        CollectionSource<ArrayList<String>> source = () -> actual;

        Condition.PreservingCondition<Collection<?>> collectionCondition = nonEmpty;
        Condition.PreservingStage<Collection<?>> explained =
                collectionCondition.because("collection full chain");
        ArrayList<String> raw = await(source).until(collectionCondition);
        ArrayList<String> selected = await(source).every(EVERY).upTo(UP_TO).persisting(ZERO).until(explained);

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void mapFacadeExecutesCanonicalFullChain() {
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> raw = await(source).until(MapConditions.nonEmpty);
        LinkedHashMap<String, String> selected = await(source).every(EVERY).upTo(UP_TO).persisting(ZERO).until(MapConditions.nonEmpty.because("map full chain"));

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    private static void assertAllSame(Object expected, Object... actuals) {
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
