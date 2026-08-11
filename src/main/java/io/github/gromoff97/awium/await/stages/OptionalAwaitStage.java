package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.OptionalSource;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.present;
import static java.util.Objects.requireNonNull;

public final class OptionalAwaitStage<T> extends AbstractAwaitStage<Optional<T>, OptionalAwait<T>> implements OptionalAwait<T> {

    public OptionalAwaitStage(OptionalSource<T> source) {
        super(source);
    }

    private OptionalAwaitStage(OptionalAwaitStage<T> stage,
            WaitConfiguration configuration) {
        super(stage, configuration);
    }

    @Override
    protected OptionalAwait<T> reconfigured(WaitConfiguration configuration) {
        return new OptionalAwaitStage<>(this, configuration);
    }

    @Override
    public T until(PresentCondition condition) {
        return complete(present(
                requireNonNull(condition, "condition must not be null")));
    }

    @Override
    public T until(PresentCondition.ExplainedCondition condition) {
        return complete(present(
                requireNonNull(condition, "condition must not be null")));
    }
}
