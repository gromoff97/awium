package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static java.lang.System.nanoTime;
import static java.lang.Thread.State.*;
import static java.lang.Thread.currentThread;
import static java.time.Duration.*;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static java.lang.Thread.ofPlatform;
import static java.lang.Thread.ofVirtual;
import static java.lang.ThreadLocal.withInitial;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

class ThreadIntegrationTest {

    @ParameterizedTest
    @MethodSource("callers")
    void callerRunsEveryObservationOnItsEnteringThread(ThreadFactory callers) throws Exception {
        List<Thread> callbackThreads = new ArrayList<>();
        int[] observations = {0};
        int[] result = {0};

        Thread caller = callers.newThread(() -> result[0] = await(() -> {
            callbackThreads.add(currentThread());
            return ++observations[0];
        }).every(ofMillis(20)).upTo(ofSeconds(2)).until(condition("third observation", value -> {
            callbackThreads.add(currentThread());
            return value == 3 ? satisfied()
                    : unsatisfied("not the third observation");
        })));
        caller.start();
        caller.join(5_000);
        if (caller.isAlive()) {
            caller.interrupt();
            caller.join(2_000);
        }

        assertFalse(caller.isAlive());
        assertEquals(3, result[0]);
        assertEquals(3, observations[0]);
        assertEquals(nCopies(6, caller), callbackThreads);
    }

    @Test
    void concurrentCallersReuseDefinitionsWithIndependentLazyState() throws Exception {
        var rendezvous = new CyclicBarrier(2);
        var sourceCalls = new AtomicInteger();
        var timers = withInitial(() -> new FakeTime(0));
        var waiting = await(() -> {
            sourceCalls.incrementAndGet();
            return currentThread();
        }).usingTime(() -> timers.get().getAsLong(), nanos -> timers.get().accept(nanos))
                .every(ofNanos(1)).upTo(ofNanos(3));
        var check = Conditions.<Thread>conditionPerWait("two observations per caller", () -> {
            Thread owner = currentThread();
            int[] calls = {0};
            return actual -> {
                if (actual != owner || currentThread() != owner) {
                    throw new IllegalStateException("callback moved to another caller");
                }
                if (++calls[0] == 1) {
                    rendezvous.await(3, SECONDS);
                    return unsatisfied("first observation");
                }
                return satisfied();
            };
        });
        var resultThreads = new HashSet<Thread>();

        try (var callers = newVirtualThreadPerTaskExecutor()) {
            var results = List.of(callers.submit(() -> diagnostic(waiting).until(check)),
                    callers.submit(() -> diagnostic(waiting).until(check)));
            for (var future : results) {
                var result = future.get(5, SECONDS);
                Thread caller = result.orThrow();
                assertTrue(caller.isVirtual());
                assertThat(attempts(result)).hasSize(2)
                        .allSatisfy(attempt -> assertSame(caller, observation(attempt)));
                resultThreads.add(caller);
            }
        }
        assertEquals(2, resultThreads.size());
        assertEquals(4, sourceCalls.get());
    }

    @ParameterizedTest
    @MethodSource("callers")
    void externalControllerCancelsTheCallerWhileItIsParked(ThreadFactory callers) throws Exception {
        Throwable[] terminal = new Throwable[1];
        boolean[] interrupted = new boolean[1];
        Thread caller = callers.newThread(() -> {
            try {
                await(() -> 1).every(ofSeconds(5)).upTo(ofSeconds(10)).until(condition(
                                "never satisfied", value ->
                                        unsatisfied("not ready")));
            } catch (Throwable failure) {
                terminal[0] = failure;
                interrupted[0] = currentThread().isInterrupted();
            }
        });
        caller.start();
        try {
            awaitTimedWaiting(caller);
        } finally {
            caller.interrupt();
            caller.join(2_000);
        }

        assertSame(TERMINATED, caller.getState());
        var failure = assertInstanceOf(
                AwaitExecutionException.class, terminal[0]);
        assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.failure().reason());
        assertInstanceOf(InterruptedException.class, failure.getCause());
        assertThat(failure).hasMessageStartingWith("Waiting interrupted");
        assertTrue(interrupted[0]);
    }

    private static void awaitTimedWaiting(Thread caller) {
        long deadline = nanoTime() + ofSeconds(2).toNanos();
        while (caller.getState() != TIMED_WAITING
                && nanoTime() < deadline) {
            parkNanos(ofMillis(1).toNanos());
        }
        assertSame(TIMED_WAITING, caller.getState());
    }

    private static Stream<Named<ThreadFactory>> callers() {
        return Stream.of(
                named("platform", ofPlatform().name("awium-platform-", 0).factory()),
                named("virtual", ofVirtual().name("awium-virtual-", 0).factory()));
    }
}
