package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CheckedCallbackTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void interruptedSleepBecomesAnOrdinaryFailureWithoutRestoringTheFlag() {
        var result = tryAwait(() -> "ready").until(condition("sleep", value -> {
            Thread.currentThread().interrupt();
            Thread.sleep(0);
            return satisfied();
        }));
        var failure = failure(result);
        assertEquals(CONDITION_FAILED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void checkedMethodReferencesWorkInSourcesAndConditions() {
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
                    "condition factory evaluator", () -> waiting.until(conditionPerWait("checked", () -> actual -> { throw cause; })),
                    "condition factory creation", () -> waiting.until(conditionPerWait("checked", () -> { throw cause; })));

            for (var callback : callbacks.entrySet()) {
                calls[0] = 0;
                Throwable failure = assertFailure(CONDITION_FAILED, callback.getValue());
                assertSame(cause, failure.getCause(), callback.getKey());
                assertFalse(Thread.currentThread().isInterrupted());
                assertEquals(1, calls[0], callback.getKey());
                assertThat(time.parkRequests).isEmpty();
            }
        }
    }

    @Test
    void checkedCallbacksStillRetryAssertionFailures() {
        int[] calls = {0};
        int result = await(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1)).until(asserted(CheckedCallbackTest::verifySecond));
        assertEquals(2, result);
        assertEquals(2, calls[0]);
        assertThat(time.parkRequests).containsExactly(1L);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"value", "optional", "captured"})
    void interruptionDuringFactoryCreationSkipsItsEvaluator(String form) {
        int[] factoryCalls = {0};
        int[] evaluatorCalls = {0};
        Callable<CheckedFunction<String, ConditionResult>> factory = () -> {
            factoryCalls[0]++;
            Thread.currentThread().interrupt();
            return actual -> { evaluatorCalls[0]++; return satisfied(); };
        };
        var result = switch (form) {
            case "value" -> tryAwait(() -> "ready").until(conditionPerWait("lazy", factory));
            case "optional" -> tryAwait(() -> Optional.of("ready")).until(hasValue(conditionPerWait("lazy", factory)));
            case "captured" -> tryAwait(() -> "ready").until(captured(conditionPerWait("lazy", factory), matches(value -> true)));
            default -> throw new AssertionError(form);
        };
        var failure = failure(result);
        assertEquals(INTERRUPTED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertEquals(1, factoryCalls[0]);
        assertEquals(0, evaluatorCalls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
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
        var result = tryAwait(() -> actual).usingTime(time, time).until(valueFor("key", conditionPerWait("nested", () -> {
            factoryCalls[0]++;
            return value -> { evaluatorCalls[0]++; return satisfied(); };
        })));

        var failure = failure(result);
        assertEquals(INTERRUPTED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertEquals(0, factoryCalls[0]);
        assertEquals(0, evaluatorCalls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void nestedAndCapturedCheckedFailuresKeepTheirContext() {
        var cause = new IOException("offline");
        var nested = conditionPerWait("checked receipt", () -> (String actual) -> { throw cause; });

        var optional = tryAwait(() -> Optional.of("ready")).usingTime(time, time).until(hasValue(nested));
        var map = tryAwait(() -> Map.of("key", "ready")).usingTime(time, time).until(valueFor("key", nested));
        var sequence = tryAwait(() -> "ready").usingTime(time, time).every(ofNanos(1)).until(captured(isNotNull, nested));
        for (AwaitResult<?, ?> result : List.of(optional, map, sequence)) {
            var failure = failure(result);
            assertSame(cause, failure.cause());
            assertThat(failure.message()).contains("checked receipt");
        }
        uncontrolled(Attempt.Operation.CONDITION, attempts(sequence).getLast());
        assertSame(nested, sequence.conditions().getLast().condition());
        assertEquals(2L, sequence.conditions().getLast().attempts().firstKey());
    }

    @Test
    void quantifiersStopBeforeTheNextCallbackAfterAnInterrupt() {
        var values = List.of(1, 2, 3);
        var visited = new ArrayList<Integer>();
        int[] sourceCalls = {0};
        var result = tryAwait(() -> { sourceCalls[0]++; return values; })
                .usingTime(time, time).until(CollectionConditions.all(value -> {
                    visited.add(value);
                    Thread.currentThread().interrupt();
                    return true;
                }));
        var failure = failure(result);
        assertEquals(INTERRUPTED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertEquals(List.of(1), visited);
        assertEquals(1, sourceCalls[0]);
        assertThat(time.parkRequests).isEmpty();
    }

    @Test
    void aFlagClearedWithinTheSameCallbackDoesNotInterruptTheQuantifier() {
        var actual = List.of(1, 2);
        var result = tryAwait(() -> actual).usingTime(time, time).until(CollectionConditions.all(value -> {
            Thread.currentThread().interrupt();
            assertTrue(Thread.interrupted());
            return true;
        }));
        assertSame(actual, result.orThrow());
        assertEquals(1, attempts(result).size());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void exceptionAfterAnInterruptWithinTheSameCallbackKeepsItsOriginalCause() {
        var cause = new IllegalStateException("callback failed");
        int[] calls = {0};
        var result = tryAwait(() -> List.of(1, 2)).until(CollectionConditions.all(value -> {
            calls[0]++;
            Thread.currentThread().interrupt();
            throw cause;
        }));
        var failure = failure(result);
        assertEquals(INTERRUPTED, failure.reason());
        assertSame(cause, failure.cause());
        assertEquals(1, calls[0]);
    }

    private static void verify(String actual) throws Throwable {
        assertEquals("ready", actual);
    }

    private static int length(String actual) throws IOException {
        return actual.length();
    }

    private static ConditionResult evaluate(String actual) throws Throwable {
        return satisfied();
    }

    private static void verifySecond(int actual) throws Throwable {
        assertEquals(2, actual);
    }
}
