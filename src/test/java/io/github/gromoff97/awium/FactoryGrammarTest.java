package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
import static io.github.gromoff97.awium.CollectionConditions.ac;
import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
import static io.github.gromoff97.awium.OptionalConditions.ao;
import static io.github.gromoff97.awium.MapConditions.awaitMap;
import static io.github.gromoff97.awium.MapConditions.am;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullRejected("source", () -> await((Callable<Object>) null));
        assertNullRejected("source", () -> awaitOptional((java.util.concurrent.Callable<java.util.Optional<Object>>) null));
        assertNullRejected("source", () -> awaitCollection((java.util.concurrent.Callable<Collection<Object>>) null));
        assertNullRejected("source", () -> awaitMap((java.util.concurrent.Callable<Map<Object, Object>>) null));
        assertNullRejected("source", () -> awaitCollection((Await.CollectionViewSource<Object, List<Object>>) null));
        assertNullRejected("source", () -> awaitMap((Await.MapViewSource<Object, Object, Map<Object, Object>>) null));
        assertNullRejected("source", () -> a(null));
        assertNullRejected("source", () -> ao(null));
        assertNullRejected("source", () -> ac((Callable<Collection<Object>>) null));
        assertNullRejected("source", () -> am((Callable<Map<Object, Object>>) null));
        assertNullRejected("source", () -> ac((Await.CollectionViewSource<Object, List<Object>>) null));
        assertNullRejected("source", () -> am((Await.MapViewSource<Object, Object, Map<Object, Object>>) null));
    }

    @Test
    void nullReturningSourcesHaveAnExplicitCategory() {
        assertNull(await(() -> null).until(isNull));
        assertNull(a(() -> null).until(isNull));
        assertNull(awaitOptional(() -> null).until(isNull));
        assertNull(ao(() -> null).until(isNull));
        assertNull(awaitCollection(() -> null).until(isNull));
        assertNull(ac(() -> null).until(isNull));
        assertNull(awaitMap(() -> null).until(isNull));
        assertNull(am(() -> null).until(isNull));
    }

    @Test
    void constructorReferencesSelectTheirSourceCategoryWithAllFactoriesImported() {
        StringBuilder text = await(StringBuilder::new).until(StringConditions.empty);
        ArrayList<String> values = awaitCollection(ArrayList<String>::new).until(CollectionConditions.empty);
        LinkedHashMap<String, Integer> entries = awaitMap(LinkedHashMap<String, Integer>::new).until(MapConditions.empty);
        Optional<?> optional = awaitOptional(Optional::empty).until(OptionalConditions.absent);

        assertTrue(text.isEmpty());
        assertTrue(values.isEmpty());
        assertTrue(entries.isEmpty());
        assertTrue(optional.isEmpty());
        assertTrue(a(StringBuilder::new).until(StringConditions.empty).isEmpty());
        assertTrue(ac(ArrayList<String>::new).until(CollectionConditions.empty).isEmpty());
        assertTrue(am(LinkedHashMap<String, Integer>::new).until(MapConditions.empty).isEmpty());
        assertTrue(ao(Optional::empty).until(OptionalConditions.absent).isEmpty());
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        var pollingTime = new FakeTime(0);
        int[] calls = {0};
        var initial = await(() -> "v" + ++calls[0]).usingTime(pollingTime, pollingTime);

        var slow = initial.every(ofSeconds(20));
        var repaired = slow.upTo(ofSeconds(10)).every(ofMillis(1)).upTo(ofSeconds(1))
                .persisting(ofSeconds(2)).persisting(ZERO);

        assertEquals("v2", repaired.until(equalTo("v2")));
        assertEquals(ofMillis(1).toNanos(), pollingTime.getAsLong());
        assertFailure(TIMEOUT,
                () -> slow.until(equalTo("never")));
        assertEquals(3, calls[0]);
        assertEquals(ofSeconds(10).plusMillis(1).toNanos(), pollingTime.getAsLong());
    }

    @Test
    void everyTerminalOverloadRejectsNullConditions() {
        var pollingTime = new FakeTime(0);
        var object = await(() -> "value").usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("predicate", () -> object.until(matches((CheckedPredicate<String>) null)));
        assertNullRejected("predicate", () -> object.tryUntil(matches((CheckedPredicate<String>) null)));
        assertNullRejected("predicate", () -> CollectionConditions.single((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> OptionalConditions.hasValue((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> MapConditions.valueFor("key", (CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> MapConditions.singleEntry((CheckedBiPredicate<String, String>) null));
        assertNullRejected("condition", () -> CollectionConditions.all((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> CollectionConditions.any((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> CollectionConditions.none((Condition.Typed<Object, String>) null));
        assertNullRejected("condition", () -> MapConditions.onlyValueFor("key", (PreservingCondition<String>) null));
        assertNullRejected("condition", () -> MapConditions.onlyValueFor("key", (Condition.Typed<Object, String>) null));
        assertNullRejected("condition", () -> object.until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> object.until((Condition.Typed<String, String>) null));
        assertNullRejected("condition", () -> object.until((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> object.tryUntil((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> object.until(equalTo("value"), equalTo("value"), (Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> OptionalConditions.hasValue((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> CollectionConditions.single((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> MapConditions.valueFor("key", (Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> MapConditions.singleEntry((Condition.ComparisonCondition<Map.Entry<String, String>>) null));

        var optional = awaitOptional((java.util.concurrent.Callable<java.util.Optional<String>>) Optional::empty).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> optional.until((SelectedCondition<Optional<?>, Object, Condition.Reference<?>>) null));

        var collection = awaitCollection((java.util.concurrent.Callable<Collection<String>>) List::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> collection.until((SelectedCondition<Collection<?>, Object, Condition.Reference<?>>) null));

        var map = awaitMap((java.util.concurrent.Callable<Map<String, String>>) Map::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> map.until((SelectedCondition<Map<?, ?>, Object, Condition.Reference<?>>) null));
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
