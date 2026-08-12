package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.Awium.await;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.ArrayList;
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
                await(source).every(EVERY)
                        .until(isNotNull.because("object every")),
                await(source).upTo(UP_TO).until(isNotNull),
                await(source).stableFor(ZERO)
                        .until(isNotNull.because("object stable")),
                await(source).every(EVERY).upTo(UP_TO)
                        .until(isNotNull),
                await(source).every(EVERY).stableFor(ZERO)
                        .until(isNotNull.because("object every stable")),
                await(source).upTo(UP_TO).stableFor(ZERO)
                        .until(isNotNull),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(isNotNull.because("object all")),
                await(source).stableFor(ZERO).upTo(UP_TO).every(EVERY)
                        .until(isNotNull),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(isNotNull));

        Condition<Object, Object> selecting = condition(
                "select actual", Evaluation::satisfied);
        assertSame(actual, await(source).until(selecting));
        assertSame(actual, await(source)
                .until(selecting.because("selected object")));
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var value = new Object();
        OptionalSource<Object> source = () -> Optional.of(value);

        assertSame(value,
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(present.because("optional full chain")));
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var actual = new ArrayList<>(List.of("value"));
        CollectionSource<ArrayList<String>> source = () -> actual;

        assertSame(actual,
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty.because("collection full chain")));
    }

    @Test
    void mapFacadeExecutesCanonicalFullChain() {
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        assertSame(actual,
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty.because("map full chain")));
    }

    private static void assertAllSame(Object expected, Object... actuals) {
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
