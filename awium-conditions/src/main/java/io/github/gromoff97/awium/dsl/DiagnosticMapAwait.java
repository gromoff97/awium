package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.dsl.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.Entry;

public final class DiagnosticMapAwait<OBSERVED extends Map<?, ?>, ENTRY extends Entry<?, ?>, VALUE>
        extends DiagnosticSelectionAwait<OBSERVED, ENTRY> {

    DiagnosticMapAwait(io.github.gromoff97.awium.DiagnosticAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new DiagnosticMapAwait<>(super.handling(factory));
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> every(Duration interval) {
        return new DiagnosticMapAwait<>(super.every(interval));
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> upTo(Duration timeout) {
        return new DiagnosticMapAwait<>(super.upTo(timeout));
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> persisting(Duration persistence) {
        return new DiagnosticMapAwait<>(super.persisting(persistence));
    }

    public AwaitResult<OBSERVED, VALUE> until(
            MapValueCondition<? super OBSERVED> condition) {
        return super.until(ConditionBinding.<OBSERVED, VALUE>bind(condition));
    }

}
