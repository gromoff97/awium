package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class CheckedCallbackTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void checkedMethodReferencesPreserveAndTransformResults() {
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);
        assertEquals("ready", waiting.until(asserted(CheckedCallbackTest::verify)));
        assertEquals(5, waiting.until(yields(CheckedCallbackTest::length)));
        assertEquals("ready", waiting.until(condition("checked evaluation", CheckedCallbackTest::evaluate)));
        assertEquals("ready", waiting.until(preserving("checked evaluation", CheckedCallbackTest::evaluate)));
    }

    @Test
    void checkedFailuresStopEveryCallbackFormOnceWithTheOriginalCause() {
        for (Exception cause : List.of(new IOException("offline"), new InterruptedException("cancelled"))) {
            var time = new FakeTime(0);
            int[] calls = {0};
            Source<String> source = () -> "value" + ++calls[0];
            var waiting = await(source).usingTime(time, time);
            Map<String, Executable> callbacks = Map.of(
                    "asserted", () -> waiting.until(asserted(actual -> { throw cause; })),
                    "yields", () -> waiting.until(yields(actual -> { throw cause; })),
                    "condition", () -> waiting.until(condition("checked", actual -> { throw cause; })),
                    "preserving", () -> waiting.until(preserving("checked", actual -> { throw cause; })),
                    "condition factory evaluator", () -> waiting.until(conditionFactory("checked", () -> actual -> { throw cause; })),
                    "preserving factory evaluator", () -> waiting.until(preservingFactory("checked", () -> actual -> { throw cause; })),
                    "condition factory creation", () -> waiting.until(conditionFactory("checked", () -> { throw cause; })),
                    "preserving factory creation", () -> waiting.until(preservingFactory("checked", () -> { throw cause; })));

            for (var callback : callbacks.entrySet()) {
                calls[0] = 0;
                try {
                    Throwable failure = cause instanceof InterruptedException
                            ? assertFailure(INTERRUPTED, callback.getValue())
                            : assertFailure(CONDITION_FAILED, callback.getValue());
                    assertSame(cause, failure.getCause(), callback.getKey());
                    assertEquals(cause instanceof InterruptedException, Thread.currentThread().isInterrupted());
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
    void nestedAndCapturedCheckedFailuresKeepTheirContextAndExplanation() {
        var cause = new IOException("offline");
        var nested = failing(cause).because("receipt is needed");
        var time = new FakeTime(0);

        var optional = await(() -> Optional.of("ready")).usingTime(time, time).tryUntil(hasValue(nested));
        var map = await(() -> Map.of("key", "ready")).usingTime(time, time).tryUntil(valueFor("key", nested));
        var sequence = await(() -> "ready").usingTime(time, time).every(ofNanos(1)).tryUntil(yields(String::trim), nested);
        for (AwaitResult<?, ?> result : List.of(optional, map, sequence)) {
            var failure = assertInstanceOf(AwaitResult.Failed.class, result);
            assertSame(cause, failure.failure().cause());
            assertTrue(failure.failure().message().contains("receipt is needed"));
        }
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, sequence.attempts().getLast().outcome()).evaluation());
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class, outcome.context());
        assertEquals(1, context.capturedStages());
        assertEquals(2, context.evaluatedStageNumber());
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

    private static Condition<String, String> failing(IOException cause) {
        return conditionFactory("checked receipt", () -> actual -> { throw cause; });
    }
}
