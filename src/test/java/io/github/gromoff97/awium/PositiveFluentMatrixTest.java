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
import static java.time.Duration.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PositiveFluentMatrixTest {

    private static final Duration EVERY = Duration.ofMillis(1);
    private static final Duration UP_TO = Duration.ofSeconds(1);

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
    void optionalFacadeExecutesEveryValidConfigurationPath() {
        var value = new Object();
        OptionalSource<Object> source = () -> Optional.of(value);

        assertAllSame(value,
                await(source).until(present),
                await(source).every(EVERY)
                        .until(present.because("optional every")),
                await(source).upTo(UP_TO).until(present),
                await(source).stableFor(ZERO)
                        .until(present.because("optional stable")),
                await(source).every(EVERY).upTo(UP_TO)
                        .until(present),
                await(source).every(EVERY).stableFor(ZERO)
                        .until(present.because("optional every stable")),
                await(source).upTo(UP_TO).stableFor(ZERO)
                        .until(present),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(present.because("optional all")),
                await(source).stableFor(ZERO).upTo(UP_TO).every(EVERY)
                        .until(present),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(present));
    }

    @Test
    void collectionFacadeExecutesEveryValidConfigurationPath() {
        var actual = new ArrayList<>(List.of("value"));
        CollectionSource<ArrayList<String>> source = () -> actual;

        assertAllSame(actual,
                await(source).until(nonEmpty),
                await(source).every(EVERY)
                        .until(nonEmpty.because("collection every")),
                await(source).upTo(UP_TO).until(nonEmpty),
                await(source).stableFor(ZERO)
                        .until(nonEmpty.because("collection stable")),
                await(source).every(EVERY).upTo(UP_TO)
                        .until(nonEmpty),
                await(source).every(EVERY).stableFor(ZERO)
                        .until(nonEmpty.because("collection every stable")),
                await(source).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty.because("collection all")),
                await(source).stableFor(ZERO).upTo(UP_TO).every(EVERY)
                        .until(nonEmpty),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty));
    }

    @Test
    void mapFacadeExecutesEveryValidConfigurationPath() {
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        assertAllSame(actual,
                await(source).until(nonEmpty),
                await(source).every(EVERY)
                        .until(nonEmpty.because("map every")),
                await(source).upTo(UP_TO).until(nonEmpty),
                await(source).stableFor(ZERO)
                        .until(nonEmpty.because("map stable")),
                await(source).every(EVERY).upTo(UP_TO)
                        .until(nonEmpty),
                await(source).every(EVERY).stableFor(ZERO)
                        .until(nonEmpty.because("map every stable")),
                await(source).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty.because("map all")),
                await(source).stableFor(ZERO).upTo(UP_TO).every(EVERY)
                        .until(nonEmpty),
                await(source).every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .every(EVERY).upTo(UP_TO).stableFor(ZERO)
                        .until(nonEmpty));
    }

    private static void assertAllSame(Object expected, Object... actuals) {
        assertEquals(10, actuals.length);
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
