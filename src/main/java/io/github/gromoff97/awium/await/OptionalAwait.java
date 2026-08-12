package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.OptionalSource;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.present;
import static java.util.Objects.requireNonNull;

public final class OptionalAwait<T> extends AbstractAwait<Optional<T>, OptionalAwait<T>> {

    public OptionalAwait(OptionalSource<T> source) {
        super(source);
    }

    private OptionalAwait(OptionalAwait<T> await,
            WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    OptionalAwait<T> reconfigured(WaitConfiguration configuration) {
        return new OptionalAwait<>(this, configuration);
    }

    public T until(PresentCondition condition) {
        return complete(present(
                requireNonNull(condition, "condition must not be null")));
    }

    public T until(PresentCondition.ExplainedCondition condition) {
        return complete(present(
                requireNonNull(condition, "condition must not be null")));
    }
}
