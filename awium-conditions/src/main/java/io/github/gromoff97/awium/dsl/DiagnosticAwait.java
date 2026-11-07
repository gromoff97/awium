package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservesObservation;
import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public sealed class DiagnosticAwait<OBSERVED> extends io.github.gromoff97.awium.DiagnosticAwait<OBSERVED> permits DiagnosticSelectionAwait {

    DiagnosticAwait(io.github.gromoff97.awium.DiagnosticAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public DiagnosticAwait<OBSERVED> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new DiagnosticAwait<>(super.handling(factory));
    }

    @Override
    public DiagnosticAwait<OBSERVED> every(Duration interval) {
        return new DiagnosticAwait<>(super.every(interval));
    }

    @Override
    public DiagnosticAwait<OBSERVED> upTo(Duration timeout) {
        return new DiagnosticAwait<>(super.upTo(timeout));
    }

    @Override
    public DiagnosticAwait<OBSERVED> persisting(Duration persistence) {
        return new DiagnosticAwait<>(super.persisting(persistence));
    }

    public AwaitResult<OBSERVED, OBSERVED> until(PreservesObservation<? super OBSERVED, ? super Reference<? super OBSERVED>> condition) {
        return super.until(ConditionBinding.<OBSERVED, OBSERVED>bind(condition));
    }

    public AwaitResult<OBSERVED, List<OBSERVED>> until(
            CollectingCondition<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return super.until(ConditionBinding.<OBSERVED, List<OBSERVED>>bind(condition));
    }
}
