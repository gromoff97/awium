package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.conditions.Conditions;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.observation;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.State.*;
import static java.lang.Thread.currentThread;
import static java.time.Duration.*;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CapturedConcurrencyTest {

    @AutoClose
    private final ExecutorService executor = newVirtualThreadPerTaskExecutor();

    @Test
    void concurrentCapturedRunsKeepStageEvaluatorsValuesAndHistoriesIndependent() throws Exception {
        var rendezvous = new CyclicBarrier(2);
        var factories = new AtomicInteger();
        var check = Conditions.conditionPerWait("two observations per stage", () -> {
            factories.incrementAndGet();
            var owner = currentThread();
            var calls = new AtomicInteger();
            return (Thread actual) -> {
                assertSame(owner, actual);
                if (calls.incrementAndGet() == 1) {
                    rendezvous.await(3, SECONDS);
                    return unsatisfied("first observation");
                }
                return satisfied(actual.getName());
            };
        });
        var sequence = captured(check, check);

        var futures = List.of(executor.submit(() -> tryAwait(Thread::currentThread)

                .every(ofNanos(1)).until(sequence)),
                executor.submit(() -> tryAwait(Thread::currentThread)

                .every(ofNanos(1)).until(sequence)));

        var callers = new HashSet<Thread>();
        for (var future : futures) {
            var result = future.get(5, SECONDS);
            var caller = result.orThrow().getFirst();
            assertThat(result.orThrow()).containsExactly(caller, caller);
            assertThat(result.conditions()).hasSize(2).allSatisfy(history -> {
                assertSame(check, history.condition());
                assertThat(history.attempts()).hasSize(2);
            });
            assertThat(attempts(result)).hasSize(4)
                    .allSatisfy(attempt -> assertSame(caller, observation(attempt)));
            callers.add(caller);
        }
        assertEquals(2, callers.size());
        assertEquals(4, factories.get());
    }
}
