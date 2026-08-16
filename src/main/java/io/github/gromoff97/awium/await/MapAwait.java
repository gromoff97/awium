package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

public final class MapAwait<M extends Map<?, ?>> extends AbstractAwait<M, MapAwait<M>> {

    MapAwait(Source<? extends M> source) {
        super(source);
    }

    MapAwait(Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private MapAwait(MapAwait<M> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    MapAwait<M> reconfigured(WaitConfiguration configuration) {
        return new MapAwait<>(this, configuration);
    }

    public M until(MapCondition condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public M until(MapCondition.ExplainedCondition condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private M complete(MapCondition condition, String explanation) {
        return complete(actual -> replaceSatisfiedResult(condition.evaluate(actual), actual),
                condition::description, explanation);
    }
}
