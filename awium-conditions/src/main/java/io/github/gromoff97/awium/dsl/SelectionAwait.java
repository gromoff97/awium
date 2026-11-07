package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.execution.AwaitHandler;
import java.time.Duration;
import java.util.function.Supplier;

public sealed class SelectionAwait<OBSERVED, ELEMENT> extends ValueAwait<OBSERVED> permits MapAwait {

    SelectionAwait(io.github.gromoff97.awium.ValueAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new SelectionAwait<>(super.handling(factory));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> every(Duration interval) {
        return new SelectionAwait<>(super.every(interval));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> upTo(Duration timeout) {
        return new SelectionAwait<>(super.upTo(timeout));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> persisting(Duration persistence) {
        return new SelectionAwait<>(super.persisting(persistence));
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return super.until(ConditionBinding.<OBSERVED, ELEMENT>bind(condition));
    }
}
