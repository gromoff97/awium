package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class CheckedCallbackTest {

    @Test
    void interruptedSleepBecomesAnOrdinaryFailureWithoutRestoringTheFlag() {
        try {
            var result = await(() -> "ready").tryUntil(condition("sleep", value -> {
                Thread.currentThread().interrupt();
                Thread.sleep(0);
                return satisfied(value);
            }));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(CONDITION_FAILED, failure.reason());
            assertInstanceOf(InterruptedException.class, failure.cause());
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void checkedMethodReferencesWorkInSourcesAndConditions() {
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);
        assertEquals("ready", waiting.until(asserted(CheckedCallbackTest::verify)));
        assertEquals(5, await(() -> length("ready")).until(isNotNull));
        assertEquals("ready", waiting.until(condition("checked evaluation", CheckedCallbackTest::evaluate)));
    }

    @Test
    void checkedFailuresStopEveryCallbackFormOnceWithTheOriginalCause() {
        for (Exception cause : List.of(new IOException("offline"), new InterruptedException("cancelled"))) {
            var time = new FakeTime(0);
            int[] calls = {0};
            Callable<String> source = () -> "value" + ++calls[0];
            var waiting = await(source).usingTime(time, time);
            Map<String, Executable> callbacks = Map.of(
                    "asserted", () -> waiting.until(asserted(actual -> { throw cause; })),
                    "condition", () -> waiting.until(condition("checked", actual -> { throw cause; })),
                    "condition factory evaluator", () -> waiting.until(condition("checked", () -> actual -> { throw cause; })),
                    "condition factory creation", () -> waiting.until(condition("checked", () -> { throw cause; })));

            for (var callback : callbacks.entrySet()) {
                calls[0] = 0;
                try {
                    Throwable failure = assertFailure(CONDITION_FAILED, callback.getValue());
                    assertSame(cause, failure.getCause(), callback.getKey());
                    assertFalse(Thread.currentThread().isInterrupted());
                    assertEquals(1, calls[0], callback.getKey());
                    assertEquals(List.of(), time.parkRequests);
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void checkedCallbacksStillRetryAssertionFailures() {
        var time = new FakeTime(0);
        int[] calls = {0};
        int result = await(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1)).until(asserted(CheckedCallbackTest::verifySecond));
        assertEquals(2, result);
        assertEquals(2, calls[0]);
        assertEquals(List.of(1L), time.parkRequests);
    }

    @Test
    void interruptionDuringFactoryCreationSkipsItsEvaluator() {
        List<Function<Callable<CheckedFunction<String, ConditionResult<String>>>, AwaitResult<?, ?>>> waits = List.of(
                factory -> await(() -> "ready").tryUntil(condition("lazy", factory)),
                factory -> await(() -> Optional.of("ready")).tryUntil(hasValue(condition("lazy", factory))),
                factory -> await(() -> "ready").tryUntil(condition("lazy", factory), matches(value -> true)));
        for (var wait : waits) {
            int[] factoryCalls = {0};
            int[] evaluatorCalls = {0};
            try {
                var result = wait.apply(() -> {
                    factoryCalls[0]++;
                    Thread.currentThread().interrupt();
                    return actual -> { evaluatorCalls[0]++; return satisfied(actual); };
                });
                var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                assertEquals(INTERRUPTED, failure.failure().reason());
                assertInstanceOf(InterruptedException.class, failure.failure().cause());
                assertEquals(1, factoryCalls[0]);
                assertEquals(0, evaluatorCalls[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringValueExtractionSkipsTheNestedFactoryAndEvaluator() {
        var entry = new AbstractMap.SimpleImmutableEntry<String, String>("key", "ready") {
            @Override
            public String getValue() {
                Thread.currentThread().interrupt();
                return super.getValue();
            }
        };
        var actual = new ProbeContainers.EntryMap<String, String>(List.of(entry));
        int[] factoryCalls = {0};
        int[] evaluatorCalls = {0};
        var result = await(() -> actual).tryUntil(valueFor("key", condition("nested", () -> {
            factoryCalls[0]++;
            return value -> { evaluatorCalls[0]++; return satisfied(value); };
        })));

        var failure = assertInstanceOf(AwaitResult.Failed.class, result);
        assertEquals(INTERRUPTED, failure.failure().reason());
        assertInstanceOf(InterruptedException.class, failure.failure().cause());
        assertEquals(0, factoryCalls[0]);
        assertEquals(0, evaluatorCalls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void nestedAndCapturedCheckedFailuresKeepTheirContextAndExplanation() {
        var cause = new IOException("offline");
        var nested = failing(cause).because("receipt is needed");
        var time = new FakeTime(0);

        var optional = await(() -> Optional.of("ready")).usingTime(time, time).tryUntil(hasValue(nested));
        var map = await(() -> Map.of("key", "ready")).usingTime(time, time).tryUntil(valueFor("key", nested));
        var sequence = await(() -> "ready").usingTime(time, time).every(ofNanos(1)).tryUntil(isNotNull, nested);
        for (AwaitResult<?, ?> result : List.of(optional, map, sequence)) {
            var failure = assertInstanceOf(AwaitResult.Failed.class, result);
            assertSame(cause, failure.failure().cause());
            assertTrue(failure.failure().message().contains("receipt is needed"));
        }
        var observation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, sequence.attempts().getLast().outcome());
        var progress = observation.progress();
        assertEquals(1, progress.capturedStages());
        assertEquals(2, progress.evaluatedStageNumber());
    }

    @Test
    void traversalCompletesBeforeInterruptStopsTheNextAttempt() {
        var time = new FakeTime(0);
        var values = List.of(1, 2, 3);
        var visited = new ArrayList<Integer>();
        int[] sourceCalls = {0};
        var result = await(() -> { sourceCalls[0]++; return values; })
                .usingTime(time, time).tryUntil(CollectionConditions.all(value -> {
                    visited.add(value);
                    Thread.currentThread().interrupt();
                    return true;
                }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(INTERRUPTED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertEquals(values, visited);
        assertEquals(1, sourceCalls[0]);
        assertTrue(time.parkRequests.isEmpty());
    }

    @Test
    void aFlagClearedWithinTheCheckIsNotAnInterruptionAtItsBoundary() {
        var actual = List.of(1, 2);
        var result = await(() -> actual).tryUntil(CollectionConditions.all(value -> {
            if (value == 1) Thread.currentThread().interrupt();
            else assertTrue(Thread.interrupted());
            return true;
        }));
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(1, result.totalAttempts());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void exceptionAfterAnInterruptWithinTheSameCheckKeepsItsOriginalCause() {
        var cause = new IllegalStateException("second element failed");
        int[] calls = {0};
        var result = await(() -> List.of(1, 2)).tryUntil(CollectionConditions.all(value -> {
            calls[0]++;
            if (value == 2) throw cause;
            Thread.currentThread().interrupt();
            return true;
        }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(INTERRUPTED, failure.reason());
        assertSame(cause, failure.cause());
        assertEquals(2, calls[0]);
    }

    private static void verify(String actual) throws IOException {
        assertEquals("ready", actual);
    }

    private static int length(String actual) throws IOException {
        return actual.length();
    }

    private static ConditionResult<String> evaluate(String actual) throws IOException {
        return satisfied(actual);
    }

    private static void verifySecond(int actual) throws IOException {
        assertEquals(2, actual);
    }

    private static PreservingCondition<String> failing(IOException cause) {
        return condition("checked receipt", () -> actual -> { throw cause; });
    }
}
