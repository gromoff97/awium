package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class Await<S> extends AbstractAwait<S, Await<S>> {

    public Await(Source<? extends S> source) {
        super(source);
    }

    Await(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<S> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<S> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }
}
