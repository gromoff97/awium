package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AwaitRunTest {
    @Test
    void stepsKeepTheirOwnVerdictWhenTheRunContinues() throws Throwable {
        Condition<String, Integer> condition = mock(CALLS_REAL_METHODS);
        doReturn(unsatisfied("pending"), satisfied(42), unsatisfied("pending again"))
                .when(condition).probe("actual");
        doReturn(satisfied("recorded")).when(condition).historyResult(any());
        var execution = new AwaitRun<>(() -> "actual", condition, () -> 0L);
        execution.initialize();

        var first = execution.poll(ACQUISITION);
        var second = execution.poll(ACQUISITION);
        var third = execution.poll(ACQUISITION);

        assertThat(List.of(first, second, third)).extracting(ExecutionStep::conditionSatisfied)
                .containsExactly(false, true, false);
    }
}
