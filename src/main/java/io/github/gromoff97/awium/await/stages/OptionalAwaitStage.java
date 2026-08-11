package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.OptionalSource;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.present;
import static java.util.Objects.requireNonNull;

public final class OptionalAwaitStage<T> extends AbstractAwaitStage<Optional<T>> implements OptionalAwait<T> {

    public OptionalAwaitStage(OptionalSource<T> source) {
        super(source);
    }

    public OptionalAwaitStage(OptionalSource<T> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private OptionalAwaitStage(OptionalAwaitStage<T> stage,
            WaitConfiguration configuration) {
        super(stage, configuration);
    }

    @Override
    public OptionalAwait<T> every(Duration interval) {
        return new OptionalAwaitStage<>(
                this, configuration().withEvery(interval));
    }

    @Override
    public OptionalAwait<T> upTo(Duration timeout) {
        return new OptionalAwaitStage<>(
                this, configuration().withUpTo(timeout));
    }

    @Override
    public OptionalAwait<T> stableFor(Duration stability) {
        return new OptionalAwaitStage<>(
                this, configuration().withStableFor(stability));
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
