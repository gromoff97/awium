package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.ofVirtual;
import static java.time.Duration.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
