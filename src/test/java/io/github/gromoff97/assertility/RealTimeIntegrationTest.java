package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class RealTimeIntegrationTest {

    @Test
    void platformCallerRetainsItsIdentityAndThreadLocalAcrossFixedDelays() {
        Thread caller = Thread.currentThread();
        ThreadLocal<String> local = new ThreadLocal<>();
        local.set("caller state");
        List<Thread> callbackThreads = new ArrayList<>();
        List<String> callbackValues = new ArrayList<>();
        List<Long> sourceStarts = new ArrayList<>();
        List<Long> conditionEnds = new ArrayList<>();
        int[] observations = {0};

        try {
            int result = Assertility.await((AwaitSources.Source<Integer>) () -> {
                sourceStarts.add(System.nanoTime());
                callbackThreads.add(Thread.currentThread());
                callbackValues.add(local.get());
                return ++observations[0];
            }).every(Duration.ofMillis(40)).upTo(Duration.ofSeconds(2))
                    .until(AwaitConditions.condition("third observation", value -> {
                        callbackThreads.add(Thread.currentThread());
                        callbackValues.add(local.get());
                        Evaluation<Integer> evaluation = value == 3
                                ? Evaluation.satisfied(value)
                                : Evaluation.unsatisfied("not the third observation");
                        conditionEnds.add(System.nanoTime());
                        return evaluation;
                    }));

            assertEquals(3, result);
            assertEquals(3, observations[0]);
            assertTrue(callbackThreads.stream().allMatch(thread -> thread == caller));
            assertTrue(callbackValues.stream().allMatch("caller state"::equals));
            assertEquals(3, sourceStarts.size());
            assertTrue(sourceStarts.get(1) - conditionEnds.get(0)
                    >= Duration.ofMillis(20).toNanos());
            assertTrue(sourceStarts.get(2) - conditionEnds.get(1)
                    >= Duration.ofMillis(20).toNanos());
        } finally {
            local.remove();
        }
    }

    @Test
    void externalControllerCancelsTheCallerWhileItIsParked() throws Exception {
        Throwable[] terminal = new Throwable[1];
        boolean[] interrupted = new boolean[1];
        Thread caller = Thread.ofPlatform().name("assertility-platform-caller")
                .unstarted(() -> {
                    try {
                        Assertility.await((AwaitSources.Source<Integer>) () -> 1)
                                .every(Duration.ofSeconds(5))
                                .upTo(Duration.ofSeconds(10))
                                .until(AwaitConditions.condition(
                                        "never satisfied", value ->
                                                Evaluation.unsatisfied("not ready")));
                    } catch (Throwable failure) {
                        terminal[0] = failure;
                        interrupted[0] = Thread.currentThread().isInterrupted();
                    }
                });
        caller.start();
        awaitTimedWaiting(caller);

        Thread controller = Thread.ofPlatform().daemon()
                .name("assertility-interrupt-controller")
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
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (caller.getState() != Thread.State.TIMED_WAITING
                && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
        }
        assertSame(Thread.State.TIMED_WAITING, caller.getState());
    }
}
