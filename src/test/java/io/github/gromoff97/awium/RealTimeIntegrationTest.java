package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.ofPlatform;
import static java.lang.Thread.State.*;
import static java.time.Duration.*;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RealTimeIntegrationTest {

    @Test
    void externalControllerCancelsTheCallerWhileItIsParked() throws Exception {
        Throwable[] terminal = new Throwable[1];
        boolean[] interrupted = new boolean[1];
        Thread caller = ofPlatform().name("awium-platform-caller")
                .unstarted(() -> {
                    try {
                        await((Source<Integer>) () -> 1).every(ofSeconds(5)).upTo(ofSeconds(10)).until(condition(
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
        assertTrue(failure.getMessage().startsWith(
                "Caller thread was interrupted while waiting"));
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
