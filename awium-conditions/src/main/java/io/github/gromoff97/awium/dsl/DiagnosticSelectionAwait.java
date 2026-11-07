package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.function.Supplier;

public sealed class DiagnosticSelectionAwait<OBSERVED, ELEMENT> extends DiagnosticAwait<OBSERVED> permits DiagnosticMapAwait {

    DiagnosticSelectionAwait(io.github.gromoff97.awium.DiagnosticAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new DiagnosticSelectionAwait<>(super.handling(factory));
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> every(Duration interval) {
        return new DiagnosticSelectionAwait<>(super.every(interval));
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> upTo(Duration timeout) {
        return new DiagnosticSelectionAwait<>(super.upTo(timeout));
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> persisting(Duration persistence) {
        return new DiagnosticSelectionAwait<>(super.persisting(persistence));
    }

    public AwaitResult<OBSERVED, ELEMENT> until(
            SelectedCondition<? super OBSERVED> condition) {
        return super.until(ConditionBinding.<OBSERVED, ELEMENT>bind(condition));
    }

}
