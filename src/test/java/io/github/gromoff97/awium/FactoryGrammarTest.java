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

import static io.github.gromoff97.awium.Await.await;
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
        assertNullRejected("source", () -> await((Await.OptionalSource<Object>) null));
        assertNullRejected("source", () -> await((Await.CollectionSource<Collection<Object>>) null));
        assertNullRejected("source", () -> await((Await.MapSource<Map<Object, Object>>) null));
        assertNullRejected("source", () -> await((Await.CollectionViewSource<Object, List<Object>>) null));
        assertNullRejected("source", () -> await((Await.MapViewSource<Object, Object, Map<Object, Object>>) null));
    }

    @Test
    void nullReturningSourcesCanDeclareTheirCategory() {
        Callable<Object> value = () -> null;
        Await.OptionalSource<String> optional = () -> null;
        Await.CollectionSource<List<String>> collection = () -> null;
        Await.MapSource<Map<String, String>> map = () -> null;
        assertNull(await(value).until(isNull));
        assertNull(await(optional).until(isNull));
        assertNull(await(collection).until(isNull));
        assertNull(await(map).until(isNull));
    }

    @Test
    void constructorLambdasSelectTheirSourceCategory() {
        StringBuilder text = await(() -> new StringBuilder()).until(StringConditions.empty);
        ArrayList<String> values = await(() -> new ArrayList<String>()).until(CollectionConditions.empty);
        LinkedHashMap<String, Integer> entries = await(() -> new LinkedHashMap<String, Integer>()).until(MapConditions.empty);
        Optional<?> optional = await(() -> Optional.empty()).until(OptionalConditions.absent);
        assertTrue(text.isEmpty());
        assertTrue(values.isEmpty());
        assertTrue(entries.isEmpty());
        assertTrue(optional.isEmpty());
    }

    @Test
    void missingConditionsAreRejectedBeforeReadingTheSource() {
        Callable<String> source = () -> { throw new AssertionError("source must not be read"); };
        var waiting = await(source);
        assertNullRejected("condition", () -> waiting.until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> waiting.tryUntil((PreservingCondition<String>) null));
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

        var optional = await((Await.OptionalSource<String>) Optional::empty).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> optional.until((SelectedCondition<Optional<?>, Object, Condition.Reference<?>>) null));

        var collection = await((Await.CollectionSource<Collection<String>>) List::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> collection.until((SelectedCondition<Collection<?>, Object, Condition.Reference<?>>) null));

        var map = await((Await.MapSource<Map<String, String>>) Map::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> map.until((SelectedCondition<Map<?, ?>, Object, Condition.Reference<?>>) null));
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
