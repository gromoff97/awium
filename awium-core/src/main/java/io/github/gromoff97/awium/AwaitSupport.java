package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

abstract sealed class AwaitSupport<OBSERVED> permits ValueAwait, DiagnosticAwait {

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;
    final Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory;

    AwaitSupport(Callable<? extends OBSERVED> source, WaitConfiguration configuration,
            Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = configuration;
        this.handlerFactory = handlerFactory;
    }

    final <RESULT> RESULT complete(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return this.<RESULT>execute(condition).orThrow();
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> execute(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return WaitEngine.execute(configuration, source, condition, handlerFactory);
    }
}
