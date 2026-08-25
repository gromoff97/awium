package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class OptionalAwait<T> extends AbstractAwait<Optional<T>, OptionalAwait<T>> {

    OptionalAwait(OptionalSource<T> source) {
        super(source);
    }

    private OptionalAwait(OptionalAwait<T> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    OptionalAwait<T> reconfigured(WaitConfiguration configuration) {
        return new OptionalAwait<>(this, configuration);
    }

    public T until(SelectedCondition<? super Optional<T>> condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public T until(SelectedCondition.ExplainedCondition<? super Optional<T>> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private T complete(SelectedCondition<? super Optional<T>> condition, String explanation) {
        return completeSelected(condition, actual -> actual.orElse(null), explanation);
    }
}
