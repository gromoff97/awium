package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.AwaitCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.description;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.explanation;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<S, A> {

    private final Source<? extends S> source;
    private final WaitEngine engine;

    protected AbstractAwait(Source<? extends S> source) {
        this(source, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.engine = new WaitEngine(requireNonNull(configuration, "configuration must not be null"),
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    AbstractAwait(AbstractAwait<S, ?> await, WaitConfiguration configuration) {
        this(await.source, configuration, await.engine.clock(), await.engine.parker());
    }

    public final A every(Duration interval) {
        return reconfigured(engine.configuration().withEvery(interval));
    }

    public final A upTo(Duration timeout) {
        return reconfigured(engine.configuration().withUpTo(timeout));
    }

    public final A persisting(Duration persistence) {
        return reconfigured(engine.configuration().withPersistence(persistence));
    }

    abstract A reconfigured(WaitConfiguration configuration);

    protected final <R> R complete(Function<? super S, ? extends Evaluation<? extends R>> evaluator,
            AwaitCondition condition) {
        return FailureFactory.complete(engine.waitFor(source, evaluator), description(condition), explanation(condition),
                engine.configuration());
    }

    protected final <R> AwaitResult<S, R> capture(Function<? super S, ? extends Evaluation<? extends R>> evaluator,
            AwaitCondition condition) {
        return FailureFactory.capture(engine.recordedWaitFor(source, evaluator), description(condition), explanation(condition),
                engine.configuration());
    }
}
