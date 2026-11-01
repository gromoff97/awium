package io.github.gromoff97.awium;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.concurrent.Callable;

public sealed class DiagnosticSelectionAwait<OBSERVED, ELEMENT> extends DiagnosticAwait<OBSERVED> permits DiagnosticMapAwait {

    DiagnosticSelectionAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> every(Duration interval) {
        return new DiagnosticSelectionAwait<>(source, configuration.withEvery(interval));
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> upTo(Duration timeout) {
        return new DiagnosticSelectionAwait<>(source, configuration.withUpTo(timeout));
    }

    @Override
    public DiagnosticSelectionAwait<OBSERVED, ELEMENT> persisting(Duration persistence) {
        return new DiagnosticSelectionAwait<>(source, configuration.withPersistence(persistence));
    }

    public AwaitResult<OBSERVED, ELEMENT> until(
            SelectedCondition<? super OBSERVED> condition) {
        return execute(condition);
    }

}
