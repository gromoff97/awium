package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.condition.Condition.PreservingStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.NarrowingStage;
import io.github.gromoff97.awium.condition.ConditionStage.ResultStage;
import io.github.gromoff97.awium.condition.Condition.SelectedStage;
import io.github.gromoff97.awium.condition.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class TryAwait<Observed, Element, Family extends Source<?>> extends AbstractAwait<Observed, TryAwait<Observed, Element, Family>> {

    TryAwait(Source<? extends Observed> source) {
        super(source);
    }

    TryAwait(Source<? extends Observed> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private TryAwait(TryAwait<Observed, Element, Family> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    TryAwait<Observed, Element, Family> reconfigured(WaitConfiguration configuration) {
        return new TryAwait<>(this, configuration);
    }

    public AwaitResult<Observed, Observed> until(PreservingStage<? super Observed> condition) {
        return capture(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Expected extends Observed> AwaitResult<Observed, Observed> until(ExpectedStage<Expected> condition) {
        return capture(ConditionRuntime.expectedEvaluator(condition), condition);
    }

    public <Expected extends Observed> AwaitResult<Observed, List<Observed>> until(ExpectedSequenceStage<Expected> condition) {
        return capture(ConditionRuntime.expectedSequenceEvaluator(condition), condition);
    }

    public <Result extends Observed> AwaitResult<Observed, Result> until(NarrowingStage<Result> condition) {
        return capture(ConditionRuntime.narrowingEvaluator(condition), condition);
    }

    public <Result> AwaitResult<Observed, Result> until(ResultStage<? super Observed, ? extends Result> condition) {
        return capture(ConditionRuntime.evaluator(condition), condition);
    }

    public AwaitResult<Observed, Element> until(SelectedStage<? super Observed, Family> condition) {
        return capture(ConditionRuntime.selectedEvaluator(condition), condition);
    }

    public AwaitResult<Observed, List<Element>> until(SelectedSequenceStage<? super Observed, Family> condition) {
        return capture(ConditionRuntime.selectedEvaluator(condition), condition);
    }
}
