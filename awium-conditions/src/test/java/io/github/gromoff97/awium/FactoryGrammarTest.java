package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.conditions.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.CollectionConditions.any;
import static io.github.gromoff97.awium.conditions.CollectionConditions.none;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullRejected("delegate", () -> new Await.CollectionViewSource<>(null));
        assertNullRejected("delegate", () -> new Await.MapViewSource<>(null));
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
    void missingConditionsAreRejectedBeforeStartingTheWait() {
        var unexpected = new AssertionError("waiting must not start");
        Callable<String> source = () -> { throw unexpected; };
        LongSupplier clock = () -> { throw unexpected; };
        LongConsumer parker = nanos -> { throw unexpected; };
        var waiting = await(source).usingTime(clock, parker);
        assertNullRejected("condition", () -> waiting.until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> waiting.until((Condition<String, String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((Condition<String, String>) null));
        assertNullRejected("condition", () -> waiting.until((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((AwaitCondition.ComparisonCondition<String>) null));
        AwaitCondition.CollectingCondition<PreservingCondition<String>> sequence = null;
        assertNullRejected("condition", () -> waiting.until(sequence));
        assertNullRejected("condition", () -> diagnostic(waiting).until(sequence));
        assertNullRejected("condition", () -> waiting.until(captured(equalTo("value"), equalTo("value"), (AwaitCondition.ComparisonCondition<String>) null)));
        assertNullRejected("condition", () -> diagnostic(waiting).until(captured(equalTo("value"), equalTo("value"), (AwaitCondition.ComparisonCondition<String>) null)));

        Await.MapSource<Map<String, String>> mapSource = () -> { throw unexpected; };
        var map = await(mapSource).usingTime(clock, parker);
        SelectedCondition<Map<?, ?>> selected = null;
        AwaitCondition.MapValueCondition<Map<?, ?>> mapped = null;
        assertNullRejected("condition", () -> map.until(selected));
        assertNullRejected("condition", () -> diagnostic(map).until(selected));
        assertNullRejected("condition", () -> map.until(mapped));
        assertNullRejected("condition", () -> diagnostic(map).until(mapped));
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
    void conditionFactoriesRejectMissingChecks() {
        assertNullRejected("predicate", () -> matches((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> singleElement((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> hasValue((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> valueFor("key", (CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> singleEntry((CheckedBiPredicate<String, String>) null));
        assertNullRejected("predicate", () -> singleEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> allEntries((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> hasEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> hasNoEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("condition", () -> all((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> any((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> none((Condition<Object, String>) null));
        assertNullRejected("condition", () -> valueFor("key", (PreservingCondition<String>) null));
        assertNullRejected("condition", () -> valueFor("key", (Condition<Object, String>) null));
        assertNullRejected("condition", () -> hasValue((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> singleElement((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> valueFor("key", (AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> singleEntry((AwaitCondition.ComparisonCondition<Map.Entry<String, String>>) null));
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
