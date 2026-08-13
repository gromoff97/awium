package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<S, A> {

    private static final FailureFactory FAILURE_FACTORY = new FailureFactory();

    private final Source<S> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;

    protected AbstractAwait(Source<? extends S> source) {
        this(source, defaults(), System::nanoTime,
                LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null")::get;
        this.configuration = requireNonNull(configuration,
                "configuration must not be null");
        this.clock = requireNonNull(clock, "clock must not be null");
        this.parker = requireNonNull(parker, "parker must not be null");
    }

    AbstractAwait(AbstractAwait<S, ?> await,
            WaitConfiguration configuration) {
        this.source = await.source;
        this.configuration = requireNonNull(configuration,
                "configuration must not be null");
        this.clock = await.clock;
        this.parker = await.parker;
    }

    public final A every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    public final A upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    public final A stableFor(Duration stability) {
        return reconfigured(configuration.withStableFor(stability));
    }

    public final S until(PreservingCondition<? super S> condition) {
        return complete(preserving(
                requireNonNull(condition, "condition must not be null")));
    }

    public final S until(
            PreservingCondition.ExplainedCondition<? super S> condition) {
        return complete(preserving(
                requireNonNull(condition, "condition must not be null")));
    }

    public final <R> R until(Condition<? super S, ? extends R> condition) {
        return complete(RuntimeCondition.<S, R>open(
                requireNonNull(condition, "condition must not be null")));
    }

    public final <R> R until(
            Condition.ExplainedCondition<? super S, ? extends R> condition) {
        return complete(RuntimeCondition.<S, R>open(
                requireNonNull(condition, "condition must not be null")));
    }

    abstract A reconfigured(WaitConfiguration configuration);

    protected final <R> R complete(RuntimeCondition<S, R> condition) {
        return FAILURE_FACTORY.complete(new WaitEngine(configuration, clock,
                parker).waitFor(source, condition), condition, configuration);
    }
}
