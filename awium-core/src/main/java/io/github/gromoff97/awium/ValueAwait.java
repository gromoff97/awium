package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public non-sealed class ValueAwait<OBSERVED> extends AwaitSupport<OBSERVED> {

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this(source, configuration, null);
    }

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration,
            Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory) {
        super(source, configuration, handlerFactory);
    }

    protected ValueAwait(ValueAwait<OBSERVED> waiting) {
        super(requireNonNull(waiting, "waiting must not be null").source,
                waiting.configuration, waiting.handlerFactory);
    }

    public ValueAwait<OBSERVED> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new ValueAwait<>(source, configuration, requireNonNull(factory, "handler factory must not be null"));
    }

    public ValueAwait<OBSERVED> every(Duration interval) {
        return new ValueAwait<>(source, configuration.withEvery(interval), handlerFactory);
    }

    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return new ValueAwait<>(source, configuration.withUpTo(timeout), handlerFactory);
    }

    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return new ValueAwait<>(source, configuration.withPersistence(persistence), handlerFactory);
    }

    public final <RESULT> RESULT until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return complete(condition);
    }
}
