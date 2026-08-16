package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

public final class CollectionAwait<C extends Collection<?>> extends AbstractAwait<C, CollectionAwait<C>> {

    CollectionAwait(Source<? extends C> source) {
        super(source);
    }

    CollectionAwait(Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private CollectionAwait(CollectionAwait<C> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    CollectionAwait<C> reconfigured(WaitConfiguration configuration) {
        return new CollectionAwait<>(this, configuration);
    }

    public C until(CollectionCondition condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public C until(CollectionCondition.ExplainedCondition condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private C complete(CollectionCondition condition, String explanation) {
        return complete(actual -> replaceSatisfiedResult(condition.evaluate(actual), actual),
                condition::description, explanation);
    }
}
