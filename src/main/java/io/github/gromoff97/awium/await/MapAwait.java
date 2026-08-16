package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.MapCondition.SingleEntry;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static java.util.Objects.requireNonNull;

public final class MapAwait<K, V, M extends Map<K, V>> extends AbstractAwait<M, MapAwait<K, V, M>> {

    MapAwait(Source<? extends M> source) {
        super(source);
    }

    MapAwait(Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private MapAwait(MapAwait<K, V, M> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    MapAwait<K, V, M> reconfigured(WaitConfiguration configuration) {
        return new MapAwait<>(this, configuration);
    }

    public Map.Entry<K, V> until(SingleEntry condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public Map.Entry<K, V> until(SingleEntry.Explained condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    private Map.Entry<K, V> complete(SingleEntry condition, String explanation) {
        return complete(actual -> {
            Evaluation<Map<?, ?>> evaluation = condition.evaluate(actual);
            Map.Entry<K, V> result = evaluation.status() == SATISFIED
                    ? actual.entrySet().iterator().next() : null;
            return replaceSatisfiedResult(evaluation, result);
        }, condition::description, explanation);
    }
}
