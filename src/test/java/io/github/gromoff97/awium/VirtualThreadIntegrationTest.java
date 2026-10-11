package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.ofVirtual;
import static java.time.Duration.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadIntegrationTest {

    @Test
    void virtualCallerRunsEveryObservationOnItsEnteringThread() throws Exception {
        List<Thread> callbackThreads = new ArrayList<>();
        int[] observations = {0};
        int[] result = {0};

        Thread caller = ofVirtual().name("awium-virtual-caller")
                .start(() -> result[0] = await((Source<Integer>) () -> {
                    callbackThreads.add(currentThread());
                    return ++observations[0];
                }).every(ofMillis(20)).upTo(ofSeconds(2)).until(condition("third observation", value -> {
                    callbackThreads.add(currentThread());
                    return value == 3 ? satisfied(value)
                            : unsatisfied("not the third observation");
                })));
        caller.join(5_000);
        if (caller.isAlive()) {
            caller.interrupt();
            caller.join(2_000);
        }

        assertFalse(caller.isAlive());
        assertEquals(3, result[0]);
        assertEquals(3, observations[0]);
        assertTrue(callbackThreads.stream().allMatch(thread -> thread == caller));
    }

    @Test
    void concurrentCallersReuseDefinitionsWithIndependentLazyState() throws Exception {
        var rendezvous = new CyclicBarrier(2);
        var sourceCalls = new AtomicInteger();
        var timers = ThreadLocal.withInitial(() -> new FakeTime(0));
        var waiting = await(() -> {
            sourceCalls.incrementAndGet();
            return currentThread();
        }).usingTime(() -> timers.get().getAsLong(), nanos -> timers.get().accept(nanos))
                .every(ofNanos(1)).upTo(ofNanos(3));
        var check = Conditions.<Thread, Thread>condition("two observations per caller", () -> {
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
                return satisfied(actual);
            };
        }).because("each execution owns its counter");
        var resultThreads = new HashSet<Thread>();

        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = List.of(callers.submit(() -> waiting.tryUntil(check)),
                    callers.submit(() -> waiting.tryUntil(check)));
            for (var future : results) {
                var result = future.get(5, SECONDS);
                var success = assertInstanceOf(AwaitResult.Satisfied.class, result);
                Thread caller = assertInstanceOf(Thread.class, success.result());
                assertTrue(caller.isVirtual());
                assertEquals(2, result.totalAttempts());
                for (var attempt : result.attempts()) {
                    assertSame(caller, assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome()).observed());
                }
                resultThreads.add(caller);
            }
        }
        assertEquals(2, resultThreads.size());
        assertEquals(4, sourceCalls.get());
    }
}
