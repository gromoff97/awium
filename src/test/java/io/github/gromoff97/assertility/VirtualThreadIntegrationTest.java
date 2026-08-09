package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VirtualThreadIntegrationTest {

    @Test
    void virtualCallerRunsEveryObservationOnItsEnteringThread() throws Exception {
        ThreadLocal<String> local = new ThreadLocal<>();
        List<Thread> callbackThreads = new ArrayList<>();
        List<String> callbackValues = new ArrayList<>();
        Thread[] enteringThread = new Thread[1];
        Throwable[] failure = new Throwable[1];
        int[] observations = {0};
        int[] result = {0};

        Runnable waitTask = () -> {
            enteringThread[0] = Thread.currentThread();
            local.set("virtual state");
            try {
                result[0] = Assertility.await((AwaitSources.Source<Integer>) () -> {
                    callbackThreads.add(Thread.currentThread());
                    callbackValues.add(local.get());
                    return ++observations[0];
                }).every(Duration.ofMillis(20)).upTo(Duration.ofSeconds(2))
                        .until(AwaitConditions.condition("third observation", value -> {
                            callbackThreads.add(Thread.currentThread());
                            callbackValues.add(local.get());
                            return value == 3 ? Evaluation.satisfied(value)
                                    : Evaluation.unsatisfied(
                                            "not the third observation");
                        }));
            } catch (Throwable thrown) {
                failure[0] = thrown;
            } finally {
                local.remove();
            }
        };

        Thread caller = Thread.ofVirtual().name("assertility-virtual-caller")
                .start(waitTask);
        caller.join(5_000);
        if (caller.isAlive()) {
            caller.interrupt();
            caller.join(2_000);
        }

        assertFalse(caller.isAlive());
        assertNull(failure[0]);
        assertTrue(caller.isVirtual());
        assertSame(caller, enteringThread[0]);
        assertEquals(3, observations[0]);
        assertEquals(3, result[0]);
        assertTrue(callbackThreads.stream().allMatch(thread -> thread == caller));
        assertTrue(callbackValues.stream().allMatch("virtual state"::equals));
    }
}
