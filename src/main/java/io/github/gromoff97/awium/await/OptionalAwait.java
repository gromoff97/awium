package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PresentCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
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

    public T until(PresentCondition condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public T until(PresentCondition.ExplainedCondition condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private T complete(PresentCondition condition, String explanation) {
        Condition<Optional<?>, Object> delegate = condition.delegate();
        return complete(actual -> {
            Evaluation<Object> evaluation = delegate.evaluate(actual);
            T result = evaluation != null && evaluation.status() == SATISFIED ? actual.orElse(null) : null;
            return replaceSatisfiedResult(evaluation, result);
        }, delegate::description, explanation);
    }
}
