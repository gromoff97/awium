package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;

import java.time.Duration;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.structural;
import static java.util.Objects.requireNonNull;

public final class StructuralAwaitStage<S> extends AbstractAwaitStage<S> implements StructuralAwait<S> {

    private final String subject;
    private final ToIntFunction<? super S> size;

    public StructuralAwaitStage(CollectionSource<? extends S> source,
            ToIntFunction<? super S> size) {
        super(source::get);
        this.subject = "collection";
        this.size = requireNonNull(size);
    }

    public StructuralAwaitStage(MapSource<? extends S> source,
            ToIntFunction<? super S> size) {
        super(source::get);
        this.subject = "map";
        this.size = requireNonNull(size);
    }

    public StructuralAwaitStage(CollectionSource<? extends S> source,
            ToIntFunction<? super S> size, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker,
            FailureFactory failureFactory) {
        super(source::get, configuration, clock, parker, failureFactory);
        this.subject = "collection";
        this.size = requireNonNull(size);
    }

    public StructuralAwaitStage(MapSource<? extends S> source,
            ToIntFunction<? super S> size, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker,
            FailureFactory failureFactory) {
        super(source::get, configuration, clock, parker, failureFactory);
        this.subject = "map";
        this.size = requireNonNull(size);
    }

    private StructuralAwaitStage(StructuralAwaitStage<S> stage,
            WaitConfiguration configuration) {
        super(stage, configuration);
        this.subject = stage.subject;
        this.size = stage.size;
    }

    @Override
    public StructuralAwait<S> every(Duration interval) {
        return new StructuralAwaitStage<>(
                this, configuration().withEvery(interval));
    }

    @Override
    public StructuralAwait<S> upTo(Duration timeout) {
        return new StructuralAwaitStage<>(
                this, configuration().withUpTo(timeout));
    }

    @Override
    public StructuralAwait<S> stableFor(Duration stability) {
        return new StructuralAwaitStage<>(
                this, configuration().withStableFor(stability));
    }

    @Override
    public S until(StructuralCondition condition) {
        return complete(structural(
                requireNonNull(condition, "condition must not be null"),
                subject, size));
    }

    @Override
    public S until(StructuralCondition.ExplainedCondition condition) {
        return complete(structural(
                requireNonNull(condition, "condition must not be null"),
                subject, size));
    }
}
