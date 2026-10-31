package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.github.gromoff97.awium.Condition.Captured;
import static io.github.gromoff97.awium.Condition.Check;
import static io.github.gromoff97.awium.internal.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

abstract sealed class AwaitSupport<OBSERVED> permits ValueAwait, DiagnosticAwait {

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;

    AwaitSupport(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = configuration;
    }

    final <RESULT> RESULT complete(Check condition) {
        return complete(condition, ConditionSupport.<OBSERVED, RESULT>sequence(condition)).getFirst();
    }

    final List<OBSERVED> complete(Captured<?> condition) {
        return complete(condition, ConditionSupport.<OBSERVED>sequence(condition));
    }

    private <VALUE> List<VALUE> complete(Condition condition, SequenceExecution<OBSERVED, VALUE> execution) {
        var history = new AttemptHistory<OBSERVED>(false);
        var outcome = new WaitEngine<>(configuration, source, execution)
                .waitFor(step -> history.add(step.stageNumber(), step.attempt()));
        if (!(outcome instanceof SatisfiedWait)) {
            AwaitFailure failure = failure(condition, outcome, configuration, history.count());
            new Failed<>(List.of(), failure).orThrow();
        }
        return execution.values();
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> capture(Check condition) {
        return capture(condition, ConditionSupport.<OBSERVED, RESULT>sequence(condition), List::getFirst);
    }

    final AwaitResult<OBSERVED, List<OBSERVED>> capture(Captured<?> condition) {
        return capture(condition, ConditionSupport.<OBSERVED>sequence(condition), identity());
    }

    private <VALUE, RESULT> AwaitResult<OBSERVED, RESULT> capture(
            Condition condition, SequenceExecution<OBSERVED, VALUE> execution, Function<List<VALUE>, RESULT> result) {
        var history = new AttemptHistory<OBSERVED>(true);
        var outcome = new WaitEngine<>(configuration, source, execution)
                .waitFor(step -> history.add(step.stageNumber(), step.attempt()));
        var conditions = history.conditions(condition);
        if (outcome instanceof SatisfiedWait) {
            return new Succeeded<>(conditions, result.apply(execution.values()));
        }
        return new Failed<>(conditions,
                failure(condition, outcome, configuration, history.count()));
    }
}
