package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.ofPlatform;
import static java.lang.Thread.State.*;
import static java.time.Duration.*;
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
    void platformCallerRetainsItsIdentityAcrossFixedDelays() {
        Duration interval = ofMillis(80);
        Duration callbackDuration = ofMillis(120);
        long minimumDelayNanos = ofMillis(50).toNanos();
        long minimumStartGapNanos = callbackDuration.plusMillis(50).toNanos();
        Thread caller = currentThread();
        List<Long> sourceStarts = new ArrayList<>();
        List<Long> conditionEnds = new ArrayList<>();
        int[] observations = {0};

        int result = await((Source<Integer>) () -> {
            sourceStarts.add(nanoTime());
            assertSame(caller, currentThread());
            return ++observations[0];
        }).every(interval).upTo(ofSeconds(2))
                .until(condition("third observation", value -> {
                    assertSame(caller, currentThread());
                    Thread.sleep(callbackDuration);
                    Evaluation<Integer> evaluation = value == 3
                            ? satisfied(value)
                            : unsatisfied("not the third observation");
                    conditionEnds.add(nanoTime());
                    return evaluation;
                }));

        assertEquals(3, result);
        assertEquals(3, observations[0]);
        for (int index = 0; index < sourceStarts.size() - 1; index++) {
            assertTrue(sourceStarts.get(index + 1) - conditionEnds.get(index)
                    >= minimumDelayNanos);
            assertTrue(sourceStarts.get(index + 1) - sourceStarts.get(index)
                    >= minimumStartGapNanos);
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
                                .every(ofSeconds(5))
                                .upTo(ofSeconds(10))
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

        caller.interrupt();
        caller.join(2_000);

        assertSame(TERMINATED, caller.getState());
        AwaitInterruptedException failure = assertInstanceOf(
                AwaitInterruptedException.class, terminal[0]);
        assertInstanceOf(InterruptedException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("Origin: waiting"));
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
}
