package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.ConditionAssessment;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.description;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.explanation;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.reference;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<Observed, Self> {

    private final Source<? extends Observed> source;
    private final WaitEngine engine;

    protected AbstractAwait(Source<? extends Observed> source) {
        this(source, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends Observed> source,
            WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.engine = new WaitEngine(requireNonNull(configuration, "configuration must not be null"),
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    AbstractAwait(AbstractAwait<Observed, ?> await, WaitConfiguration configuration) {
        this(await.source, configuration, await.engine.clock(), await.engine.parker());
    }

    public final Self every(Duration interval) {
        return reconfigured(engine.configuration().withEvery(interval));
    }

    public final Self upTo(Duration timeout) {
        return reconfigured(engine.configuration().withUpTo(timeout));
    }

    public final Self persisting(Duration persistence) {
        return reconfigured(engine.configuration().withPersistence(persistence));
    }

    abstract Self reconfigured(WaitConfiguration configuration);

    protected final <Result> Result complete(Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator,
            AwaitCondition condition) {
        return FailureFactory.complete(engine.waitFor(source, evaluator), description(condition), explanation(condition),
                reference(condition), engine.configuration());
    }

    protected final <Result> AwaitResult<Observed, Result> capture(Function<? super Observed,
            ? extends ConditionAssessment<? extends Result>> evaluator,
            AwaitCondition condition) {
        return FailureFactory.capture(engine.recordedWaitFor(source, evaluator), description(condition), explanation(condition),
                reference(condition), engine.configuration());
    }
}
