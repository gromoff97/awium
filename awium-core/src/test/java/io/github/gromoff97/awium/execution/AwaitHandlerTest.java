package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.execution.AwaitHandler.WaitStarted;
import static io.github.gromoff97.awium.execution.AwaitHandler.AttemptStarted;
import static io.github.gromoff97.awium.execution.AwaitHandler.AttemptFinished;
import static io.github.gromoff97.awium.execution.AwaitHandler.WaitFinished;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static io.github.gromoff97.awium.support.TestConditions.selecting;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AwaitHandlerTest {

    private final FakeTime time = new FakeTime(0);

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @Test
    void callbacksSurroundRootBindingSourceAndProbe() {
        var handler = new RecordingHandler<String>();
        var condition = new Condition<String, Integer>() {
            @Override public String description() { return "name length"; }
            @Override public Condition<String, Integer> newRun() {
                handler.events.add("bind");
                return this;
            }
            @Override public AttemptResult<Integer> probe(String actual) {
                handler.events.add("probe");
                time.advanceNanos(3);
                return satisfied(actual.length());
            }
        };
        var result = withTime(tryAwait(() -> {
            handler.events.add("source");
            time.advanceNanos(2);
            return "ready";
        }).handling(() -> handler), time, time).every(ofNanos(1)).upTo(ofNanos(10)).until(condition);

        assertEquals(5, result.orThrow());
        assertThat(handler.events).containsExactly("start", "bind", "before 1", "source", "probe", "after 1", "finish");
        assertSame(condition, handler.start.condition());
        assertEquals(ofNanos(1), handler.start.interval());
        assertEquals(ofNanos(10), handler.start.timeout());
        assertEquals(ofNanos(0), handler.starts.getFirst().elapsed());
        assertEquals(ofNanos(5), handler.finishes.getFirst().elapsed());
        assertEquals(ofNanos(5), handler.finish.elapsed());
        assertTrue(handler.finish.failureReason().isEmpty());
    }

    @Test
    void rootVerdictsRemainDistinctFromTheHistoryReportedByTheCondition() {
        var handler = new RecordingHandler<String>();
        var condition = new Condition<String, String>() {
            private int calls;
            @Override public String description() { return "two observations"; }
            @Override public AttemptResult<String> probe(String actual) {
                if (++calls == 1) return unsatisfied("sequence is incomplete");
                return satisfied(actual);
            }
            @Override public AttemptResult<?> historyResult(AttemptResult<? extends String> result) {
                return satisfied(null);
            }
        };

        var result = withTime(tryAwait(() -> "actual").handling(() -> handler), time, time)
                .every(ofNanos(1)).until(condition);

        assertEquals("actual", result.orThrow());
        assertInstanceOf(UnsatisfiedObservation.class, handler.finishes.getFirst().attempt());
        assertInstanceOf(SatisfiedObservation.class, handler.finishes.getFirst().historyAttempt());
        assertInstanceOf(SatisfiedObservation.class, handler.finishes.getLast().attempt());
        assertThat(handler.events).containsExactly("start", "before 1", "after 1", "before 2", "after 2", "finish");
    }

    @Test
    void aHandlerCanRetainEveryAttemptInsteadOfGroupingThem() {
        var events = new ArrayList<AttemptFinished<Integer>>();
        var handler = new AwaitHandler<Integer>() {
            private WaitStarted start;
            @Override public void onStart(WaitStarted value) { start = value; }
            @Override public void onAttemptFinish(AttemptFinished<Integer> event) { events.add(event); }
            @Override public List<ConditionHistory<Integer>> onFinish(WaitFinished finish) {
                var attempts = new TreeMap<Long, Attempt<Integer>>();
                for (var event : events) attempts.put(event.number(), event.attempt());
                return List.of(new ConditionHistory<>(start.condition(), attempts));
            }
        };
        var result = withTime(tryAwait(() -> 0).handling(() -> handler), time, time)
                .every(ofNanos(1)).upTo(ofNanos(3)).until(expected(1));

        assertEquals(TIMEOUT, failure(result).reason());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 3L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eachTerminalCreatesItsOwnHandlerAndPreservesSettingsInAnyOrder(boolean diagnostic) {
        var handlers = new ArrayList<RecordingHandler<String>>();
        var waiting = withTime(await(() -> "ready"), time, time).every(ofNanos(2)).handling(() -> {
            var handler = new RecordingHandler<String>();
            handlers.add(handler);
            return handler;
        }).upTo(ofNanos(10)).persisting(ofNanos(3));

        for (int i = 0; i < 2; i++) {
            if (diagnostic) assertEquals("ready", diagnostic(waiting).until(accepted()).orThrow());
            else assertEquals("ready", waiting.until(accepted()));
        }

        assertThat(handlers).hasSize(2);
        for (var handler : handlers) {
            assertThat(handler.starts).extracting(AttemptStarted::number).containsExactly(1L, 2L, 3L);
            assertEquals(ofNanos(3), handler.start.persistence());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "before", "after", "finish"})
    void callbackFailuresStopTheWaitWithoutRecursivelyInvokingTheCallback(String at) {
        var cause = new AssertionError("handler failed");
        int[] sources = {0};
        var handler = failing(at, cause);
        var result = withTime(tryAwait(() -> { sources[0]++; return "actual"; }).handling(() -> handler), time, time)
                .until(accepted());

        assertSame(cause, failure(WAIT_FAILED, result).cause());
        assertEquals(at.equals("start") || at.equals("before") ? 0 : 1, sources[0]);
        assertEquals(1, handler.events.stream().filter("finish"::equals).count());
        assertEquals(1, handler.starts.size());
        assertEquals(1, handler.finishes.size());
    }

    @Test
    void handlerFailuresDoNotReplaceTheSourceFailure() {
        var source = new IllegalStateException("source failed");
        var callback = new AssertionError("handler failed");
        var handler = failing("after", callback);
        var result = tryAwait((Callable<String>) () -> { throw source; }).handling(() -> handler).until(accepted());

        assertSame(source, failure(SOURCE_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(callback);
        assertInstanceOf(FailedBeforeObservation.class, handler.finishes.getFirst().attempt());
    }

    @Test
    void failuresBetweenPollsStillHavePairedAttemptEvents() {
        var cause = new AssertionError("parking failed");
        var handler = new RecordingHandler<Integer>();
        var result = withTime(tryAwait(() -> 0).handling(() -> handler), time, nanos -> { throw cause; })
                .every(ofNanos(1)).until(expected(1));

        assertSame(cause, failure(WAIT_FAILED, result).cause());
        assertThat(handler.events).containsExactly("start", "before 1", "after 1", "before 2", "after 2", "finish");
        assertInstanceOf(FailedBeforeObservation.class, handler.finishes.getLast().attempt());
    }

    @Test
    void timeSpentInCallbacksCountsTowardsTheDeadline() {
        var handler = new DefaultAwaitHandler<String>() {
            @Override public void onAttemptFinish(AttemptFinished<String> event) {
                super.onAttemptFinish(event);
                time.advanceNanos(10);
            }
        };
        var result = withTime(tryAwait(() -> "ready").handling(() -> handler), time, time)
                .upTo(ofNanos(10)).until(accepted());

        assertEquals(TIMEOUT, failure(result).reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "before", "after", "finish"})
    void callbacksCannotEraseAnExistingInterrupt(String at) {
        var handler = new RecordingHandler<String>() {
            @Override public void onStart(WaitStarted event) {
                super.onStart(event);
                if (at.equals("start")) interrupted();
            }
            @Override public void onAttemptStart(AttemptStarted event) {
                super.onAttemptStart(event);
                if (at.equals("before")) interrupted();
            }
            @Override public void onAttemptFinish(AttemptFinished<String> event) {
                super.onAttemptFinish(event);
                if (at.equals("after")) interrupted();
            }
            @Override public List<ConditionHistory<String>> onFinish(WaitFinished event) {
                if (at.equals("finish")) interrupted();
                return super.onFinish(event);
            }
        };
        currentThread().interrupt();

        var result = tryAwait(() -> "ready").handling(() -> handler).until(accepted());

        assertEquals(INTERRUPTED, failure(result).reason());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void interruptionFromTheFinishCallbackCannotProduceASuccessfulWait() {
        var handler = new DefaultAwaitHandler<String>() {
            @Override public List<ConditionHistory<String>> onFinish(WaitFinished event) {
                currentThread().interrupt();
                return super.onFinish(event);
            }
        };

        var result = tryAwait(() -> "ready").handling(() -> handler).until(accepted());

        assertEquals(INTERRUPTED, failure(result).reason());
    }

    @Test
    void aNullHandlerFactoryIsRejectedWhenConfiguringTheChain() {
        assertThrows(NullPointerException.class, () -> await(() -> "value").handling(null));
    }

    @Test
    void aFactoryReturningNullProducesAnExecutionFailure() {
        var missingHandler = tryAwait(() -> "value").handling(() -> null).until(accepted());
        assertInstanceOf(NullPointerException.class, failure(WAIT_FAILED, missingHandler).cause());

    }

    @Test
    void aHandlerReturningNullHistoriesProducesAnExecutionFailure() {
        var missingHistory = tryAwait(() -> "value").handling(() -> new DefaultAwaitHandler<String>() {
            @Override public List<ConditionHistory<String>> onFinish(WaitFinished event) { return null; }
        }).until(accepted());
        assertInstanceOf(NullPointerException.class, failure(WAIT_FAILED, missingHistory).cause());
    }

    @Test
    void handlingPreservesCollectionOptionalAndMapSelectionTypes() {
        String collection = await(() -> List.of("value")).handling(DefaultAwaitHandler::new)
                .every(ofNanos(1)).until(selecting((List<String> values) -> satisfied(values.getFirst())));
        String optional = await(() -> Optional.of("value")).handling(DefaultAwaitHandler::new)
                .upTo(ofNanos(1_000_000_000)).until(selecting((Optional<String> value) -> satisfied(value.orElseThrow())));
        String mapValue = await(() -> Map.of(1, "value")).handling(DefaultAwaitHandler::new)
                .persisting(ofNanos(0)).until(selecting((Map<Integer, String> values) -> satisfied(values.get(1))));
        AwaitResult<Map<Integer, String>, String> diagnostic = tryAwait(() -> Map.of(1, "value"))
                .handling(DefaultAwaitHandler::new).until(selecting((Map<Integer, String> values) -> satisfied(values.get(1))));

        assertThat(List.of(collection, optional, mapValue, diagnostic.orThrow())).containsOnly("value");
    }

    private static RecordingHandler<String> failing(String at, Error failure) {
        return new RecordingHandler<>() {
            @Override public void onStart(WaitStarted event) {
                super.onStart(event);
                if (at.equals("start")) throw failure;
            }
            @Override public void onAttemptStart(AttemptStarted event) {
                super.onAttemptStart(event);
                if (at.equals("before")) throw failure;
            }
            @Override public void onAttemptFinish(AttemptFinished<String> event) {
                super.onAttemptFinish(event);
                if (at.equals("after")) throw failure;
            }
            @Override public List<ConditionHistory<String>> onFinish(WaitFinished event) {
                var result = super.onFinish(event);
                if (at.equals("finish")) throw failure;
                return result;
            }
        };
    }

    private static class RecordingHandler<OBSERVED> extends DefaultAwaitHandler<OBSERVED> {
        private final List<String> events = new ArrayList<>();
        private final List<AttemptStarted> starts = new ArrayList<>();
        private final List<AttemptFinished<OBSERVED>> finishes = new ArrayList<>();
        private WaitStarted start;
        private WaitFinished finish;

        @Override public void onStart(WaitStarted event) {
            super.onStart(event);
            start = event;
            events.add("start");
        }
        @Override public void onAttemptStart(AttemptStarted event) {
            starts.add(event);
            events.add("before " + event.number());
        }
        @Override public void onAttemptFinish(AttemptFinished<OBSERVED> event) {
            super.onAttemptFinish(event);
            finishes.add(event);
            events.add("after " + event.number());
        }
        @Override public List<ConditionHistory<OBSERVED>> onFinish(WaitFinished event) {
            finish = event;
            events.add("finish");
            return super.onFinish(event);
        }
    }
}
