package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.Await;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class SourceContractTest {

    @ParameterizedTest
    @MethodSource("missingSources")
    void rejectsMissingSourcesBeforePolling(String argument, Executable action) {
        assertNullRejected(argument, action);
    }

    @Test
    void constructorReferencesKeepTheirConcreteTypes() {
        var value = await(StringBuilder::new).until(accepted());
        assertEquals(0, value.length());
    }

    @Test
    void genericMethodReferencesNeedNoSourceCategory() {
        var value = await(java.util.Optional::empty).until(accepted());
        assertEquals(java.util.Optional.empty(), value);
    }

    @Test
    void nullReturningSourcesNeedNoCategory() {
        assertNull(await(() -> null).until(accepted()));
    }

    @Test
    void missingConditionsAreRejectedBeforeStartingTheWait() {
        var unexpected = new AssertionError("waiting must not start");
        Callable<String> source = () -> { throw unexpected; };
        LongSupplier clock = () -> { throw unexpected; };
        LongConsumer parker = nanos -> { throw unexpected; };
        var waiting = withTime(await(source), clock, parker);
        assertNullRejected("condition", () -> waiting.until(null));
        assertNullRejected("condition", () -> diagnostic(waiting).until(null));
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

    private static Stream<Arguments> missingSources() {
        return Stream.of(
                argumentSet("await / value", "source", (Executable) () -> await((Callable<Object>) null)),
                argumentSet("tryAwait / value", "source", (Executable) () -> tryAwait((Callable<Object>) null)));
    }
}
