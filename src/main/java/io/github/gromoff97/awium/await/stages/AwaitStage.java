package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class AwaitStage<S> extends AbstractAwaitStage<S>
        implements Await<S>, Await.Until<S>, Await.AfterEvery<S>,
                Await.AfterUpTo<S> {

    public AwaitStage(Source<S> source) {
        super(source);
    }

    public AwaitStage(Source<S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker,
            FailureFactory failureFactory) {
        super(source, configuration, clock, parker, failureFactory);
    }

    private AwaitStage(AwaitStage<S> stage,
            WaitConfiguration configuration) {
        super(stage, configuration);
    }

    @Override
    public Await.AfterEvery<S> every(Duration interval) {
        return new AwaitStage<>(this, configuration().withEvery(interval));
    }

    @Override
    public Await.AfterUpTo<S> upTo(Duration timeout) {
        return new AwaitStage<>(this, configuration().withUpTo(timeout));
    }

    @Override
    public Await.Until<S> stableFor(Duration stability) {
        return new AwaitStage<>(this,
                configuration().withStableFor(stability));
    }
}
