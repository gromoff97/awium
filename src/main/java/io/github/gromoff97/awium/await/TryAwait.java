package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class TryAwait<S, E, F extends Source<?>>
        extends AbstractAwait<S, TryAwait<S, E, F>> {

    TryAwait(Source<? extends S> source) {
        super(source);
    }

    TryAwait(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private TryAwait(TryAwait<S, E, F> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    TryAwait<S, E, F> reconfigured(WaitConfiguration configuration) {
        return new TryAwait<>(this, configuration);
    }

    public AwaitResult<S, S> until(PreservingStage<? super S> condition) {
        return capture(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <R> AwaitResult<S, R> until(ResultStage<? super S, ? extends R> condition) {
        return capture(condition.newEvaluator(), condition);
    }

    public AwaitResult<S, E> until(SelectedStage<? super S, F> condition) {
        return capture(ConditionRuntime.selectedEvaluator(condition), condition);
    }

    public AwaitResult<S, List<E>> until(SelectedSequenceStage<? super S, F> condition) {
        return capture(ConditionRuntime.selectedEvaluator(condition), condition);
    }
}
