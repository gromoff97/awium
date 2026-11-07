package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public non-sealed class DiagnosticAwait<OBSERVED> extends AwaitSupport<OBSERVED> {

    DiagnosticAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this(source, configuration, null);
    }

    DiagnosticAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration,
            Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory) {
        super(source, configuration, handlerFactory);
    }

    protected DiagnosticAwait(DiagnosticAwait<OBSERVED> waiting) {
        super(requireNonNull(waiting, "waiting must not be null").source,
                waiting.configuration, waiting.handlerFactory);
    }

    public DiagnosticAwait<OBSERVED> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new DiagnosticAwait<>(source, configuration, requireNonNull(factory, "handler factory must not be null"));
    }

    public DiagnosticAwait<OBSERVED> every(Duration interval) {
        return new DiagnosticAwait<>(source, configuration.withEvery(interval), handlerFactory);
    }

    public DiagnosticAwait<OBSERVED> upTo(Duration timeout) {
        return new DiagnosticAwait<>(source, configuration.withUpTo(timeout), handlerFactory);
    }

    public DiagnosticAwait<OBSERVED> persisting(Duration persistence) {
        return new DiagnosticAwait<>(source, configuration.withPersistence(persistence), handlerFactory);
    }

    public final <RESULT> AwaitResult<OBSERVED, RESULT> until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return execute(condition);
    }
}
