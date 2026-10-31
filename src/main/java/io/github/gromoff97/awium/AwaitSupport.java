package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptFactory;
import io.github.gromoff97.awium.internal.FailureDiagnostics;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

abstract sealed class AwaitSupport<OBSERVED> permits ValueAwait, DiagnosticAwait {

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;

    AwaitSupport(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = configuration;
    }

    final <RESULT> RESULT complete(AttemptFactory<OBSERVED, RESULT> attempt) {
        return complete(new SequenceExecution<>(attempt)).getFirst();
    }

    final <VALUE> List<VALUE> complete(SequenceExecution<OBSERVED, VALUE> execution) {
        var history = new AttemptHistory<OBSERVED>(false);
        var outcome = new WaitEngine<>(configuration, source, execution).waitFor(history::add);
        complete(outcome, configuration, history.count());
        return execution.values();
    }

    private static void complete(WaitCompletion outcome,
            WaitConfiguration configuration, long attemptNumber) {
        if (outcome instanceof WaitCompletion.Satisfied) {
            return;
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, configuration, attemptNumber);
        new AwaitResult.Failed<>(List.of(), failure).orThrow();
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> capture(AttemptFactory<OBSERVED, RESULT> attempt) {
        return capture(new SequenceExecution<>(attempt), List::getFirst);
    }

    final AwaitResult<OBSERVED, List<OBSERVED>> capture(SequenceExecution<OBSERVED, OBSERVED> execution) {
        return capture(execution, Function.identity());
    }

    private <VALUE, RESULT> AwaitResult<OBSERVED, RESULT> capture(
            SequenceExecution<OBSERVED, VALUE> execution, Function<List<VALUE>, RESULT> result) {
        var history = new AttemptHistory<OBSERVED>(true);
        var outcome = new WaitEngine<>(configuration, source, execution).waitFor(history::add);
        var conditions = history.conditions(execution.conditions());
        if (outcome instanceof WaitCompletion.Satisfied) {
            return new AwaitResult.Satisfied<>(conditions, result.apply(execution.values()));
        }
        return new AwaitResult.Failed<>(conditions,
                FailureDiagnostics.failure(outcome, configuration, history.count()));
    }
}
