package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.CollectionSource;
import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.Await.OptionalSource;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.Collection;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullRejected("source", () -> await((Callable<Object>) null));
        assertNullRejected("source", () -> await((OptionalSource<Object>) null));
        assertNullRejected("source", () -> await((CollectionSource<Collection<Object>>) null));
        assertNullRejected("source", () -> await((MapSource<Map<Object, Object>>) null));
        assertNullRejected("source", () -> await((Await.CollectionViewSource<Object, List<Object>>) null));
        assertNullRejected("source", () -> await((Await.MapViewSource<Object, Object, Map<Object, Object>>) null));
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
        assertNullRejected("condition", () -> object.until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> object.until((Condition<String, String>) null));
        assertNullRejected("condition", () -> object.until((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> object.tryUntil((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> object.until(equalTo("value"), equalTo("value"), (Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> OptionalConditions.hasValue((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> CollectionConditions.single((Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> MapConditions.valueFor("key", (Condition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> MapConditions.singleEntry((Condition.ComparisonCondition<Map.Entry<String, String>>) null));

        var optional = await((OptionalSource<String>) Optional::empty).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> optional.until((SelectedCondition<Optional<?>>) null));

        var collection = await((CollectionSource<Collection<String>>) List::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> collection.until((SelectedCondition<Collection<?>>) null));

        var map = await((MapSource<Map<String, String>>) Map::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNullRejected("condition", () -> map.until((SelectedCondition<Map<?, ?>>) null));
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
