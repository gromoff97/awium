package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.SingleElement;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static java.util.Objects.requireNonNull;

public final class CollectionAwait<E, C extends Collection<E>> extends AbstractAwait<C, CollectionAwait<E, C>> {

    CollectionAwait(Source<? extends C> source) {
        super(source);
    }

    CollectionAwait(Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private CollectionAwait(CollectionAwait<E, C> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    CollectionAwait<E, C> reconfigured(WaitConfiguration configuration) {
        return new CollectionAwait<>(this, configuration);
    }

    public E until(SingleElement condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public E until(SingleElement.Explained condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private E complete(SingleElement condition, String explanation) {
        return complete(actual -> {
            Evaluation<Collection<?>> evaluation = condition.evaluate(actual);
            E result = evaluation.status() == SATISFIED ? actual.iterator().next() : null;
            return replaceSatisfiedResult(evaluation, result);
        }, condition::description, explanation);
    }
}
