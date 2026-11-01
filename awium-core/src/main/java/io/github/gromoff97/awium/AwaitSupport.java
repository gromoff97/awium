package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

abstract sealed class AwaitSupport<OBSERVED> permits ValueAwait, DiagnosticAwait {

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;

    AwaitSupport(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = configuration;
    }

    final <RESULT> RESULT complete(AwaitCondition condition) {
        return this.<RESULT>execute(condition).orThrow();
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> execute(AwaitCondition condition) {
        var execution = ConditionExecution.<OBSERVED, RESULT>start(condition);
        var outcome = new WaitEngine<>(configuration, source, execution).waitFor();
        return execution.finish(outcome, configuration);
    }
}
