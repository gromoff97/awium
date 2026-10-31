package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.Await;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.support.AwaitAssertions.assertFailure;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceContractTest {

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
        assertNull(await(value).until(accepted));
        assertNull(await(optional).until(accepted));
        assertNull(await(collection).until(accepted));
        assertNull(await(map).until(accepted));
    }

    @Test
    void missingConditionsAreRejectedBeforeStartingTheWait() {
        var unexpected = new AssertionError("waiting must not start");
        Callable<String> source = () -> { throw unexpected; };
        LongSupplier clock = () -> { throw unexpected; };
        LongConsumer parker = nanos -> { throw unexpected; };
        var waiting = withTime(await(source), clock, parker);
        assertNullRejected("condition", () -> waiting.until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> waiting.until((Condition<String, String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((Condition<String, String>) null));
        assertNullRejected("condition", () -> waiting.until((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> diagnostic(waiting).until((AwaitCondition.ComparisonCondition<String>) null));
        AwaitCondition.CollectingCondition<PreservingCondition<String>> sequence = null;
        assertNullRejected("condition", () -> waiting.until(sequence));
        assertNullRejected("condition", () -> diagnostic(waiting).until(sequence));

        Await.MapSource<Map<String, String>> mapSource = () -> { throw unexpected; };
        var map = withTime(await(mapSource), clock, parker);
        SelectedCondition<Map<?, ?>> selected = null;
        AwaitCondition.MapValueCondition<Map<?, ?>> mapped = null;
        assertNullRejected("condition", () -> map.until(selected));
        assertNullRejected("condition", () -> withTime(tryAwait(mapSource), clock, parker).until(selected));
        assertNullRejected("condition", () -> map.until(mapped));
        assertNullRejected("condition", () -> withTime(tryAwait(mapSource), clock, parker).until(mapped));
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        var pollingTime = new FakeTime(0);
        int[] calls = {0};
        var initial = withTime(await(() -> "v" + ++calls[0]), pollingTime, pollingTime);

        var slow = initial.every(ofSeconds(20));
        var repaired = slow.upTo(ofSeconds(10)).every(ofMillis(1)).upTo(ofSeconds(1))
                .persisting(ofSeconds(2)).persisting(ZERO);

        assertEquals("v2", repaired.until(expected("v2")));
        assertEquals(ofMillis(1).toNanos(), pollingTime.getAsLong());
        assertFailure(TIMEOUT,
                () -> slow.until(expected("never")));
        assertEquals(3, calls[0]);
        assertEquals(ofSeconds(10).plusMillis(1).toNanos(), pollingTime.getAsLong());
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
