package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.ofPlatform;
import static java.util.concurrent.locks.LockSupport.parkNanos;

import io.github.gromoff97.awium.conditioning.*;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealTimeIntegrationTest {

    @Test
    void platformCallerRetainsItsIdentityAndThreadLocalAcrossFixedDelays() {
        Duration interval = Duration.ofMillis(80);
        Duration callbackDuration = Duration.ofMillis(120);
        long minimumDelayNanos = Duration.ofMillis(50).toNanos();
        long minimumStartGapNanos = callbackDuration.plusMillis(50).toNanos();
        Thread caller = currentThread();
        ThreadLocal<String> local = new ThreadLocal<>();
        local.set("caller state");
        List<Thread> callbackThreads = new ArrayList<>();
        List<String> callbackValues = new ArrayList<>();
        List<Long> sourceStarts = new ArrayList<>();
        List<Long> conditionStarts = new ArrayList<>();
        List<Long> conditionEnds = new ArrayList<>();
        int[] observations = {0};

        try {
            int result = await((Source<Integer>) () -> {
                sourceStarts.add(nanoTime());
                callbackThreads.add(currentThread());
                callbackValues.add(local.get());
                return ++observations[0];
            }).every(interval).upTo(Duration.ofSeconds(2))
                    .until(condition("third observation", value -> {
                        callbackThreads.add(currentThread());
                        callbackValues.add(local.get());
                        conditionStarts.add(nanoTime());
                        parkFor(callbackDuration);
                        Evaluation<Integer> evaluation = value == 3
                                ? satisfied(value)
                                : unsatisfied("not the third observation");
                        conditionEnds.add(nanoTime());
                        return evaluation;
                    }));

            assertEquals(3, result);
            assertEquals(3, observations[0]);
            assertTrue(callbackThreads.stream().allMatch(thread -> thread == caller));
            assertTrue(callbackValues.stream().allMatch("caller state"::equals));
            assertEquals(3, sourceStarts.size());
            for (int index = 0; index < sourceStarts.size() - 1; index++) {
                assertTrue(conditionEnds.get(index) - conditionStarts.get(index)
                        >= callbackDuration.toNanos());
                assertTrue(sourceStarts.get(index + 1) - conditionEnds.get(index)
                        >= minimumDelayNanos);
                assertTrue(sourceStarts.get(index + 1) - sourceStarts.get(index)
                        >= minimumStartGapNanos);
            }
        } finally {
            local.remove();
        }
    }

    @Test
    void externalControllerCancelsTheCallerWhileItIsParked() throws Exception {
        Throwable[] terminal = new Throwable[1];
        boolean[] interrupted = new boolean[1];
        Thread caller = ofPlatform().name("awium-platform-caller")
                .unstarted(() -> {
                    try {
                        await((Source<Integer>) () -> 1)
                                .every(Duration.ofSeconds(5))
                                .upTo(Duration.ofSeconds(10))
                                .until(condition(
                                        "never satisfied", value ->
                                                unsatisfied("not ready")));
                    } catch (Throwable failure) {
                        terminal[0] = failure;
                        interrupted[0] = currentThread().isInterrupted();
                    }
                });
        caller.start();
        awaitTimedWaiting(caller);

        Thread controller = ofPlatform().daemon()
                .name("awium-interrupt-controller")
                .start(caller::interrupt);
        controller.join(2_000);
        caller.join(2_000);

        assertSame(Thread.State.TERMINATED, controller.getState());
        assertSame(Thread.State.TERMINATED, caller.getState());
        AwaitInterruptedException failure = assertInstanceOf(
                AwaitInterruptedException.class, terminal[0]);
        assertInstanceOf(InterruptedException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("Origin: waiting"));
        assertTrue(interrupted[0]);
    }

    private static void awaitTimedWaiting(Thread caller) {
        long deadline = nanoTime() + Duration.ofSeconds(2).toNanos();
        while (caller.getState() != Thread.State.TIMED_WAITING
                && nanoTime() < deadline) {
            parkNanos(Duration.ofMillis(1).toNanos());
        }
        assertSame(Thread.State.TIMED_WAITING, caller.getState());
    }

    private static void parkFor(Duration duration) {
        long deadline = nanoTime() + duration.toNanos();
        long remaining;
        while ((remaining = deadline - nanoTime()) > 0) {
            parkNanos(remaining);
        }
    }
}
