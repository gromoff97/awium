package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.internal.diagnostic.FailureFactory;
import io.github.gromoff97.awium.sources.Source;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public abstract class AbstractAwaitStage<S> {

    private final Source<S> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;
    private final FailureFactory failureFactory;

    protected AbstractAwaitStage(Source<S> source) {
        this(source, WaitConfiguration.defaults(), System::nanoTime,
                LockSupport::parkNanos, new FailureFactory());
    }

    protected AbstractAwaitStage(Source<S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker, FailureFactory failureFactory) {
        this.source = Objects.requireNonNull(source);
        this.configuration = Objects.requireNonNull(configuration);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
        this.failureFactory = Objects.requireNonNull(failureFactory);
    }

    protected AbstractAwaitStage(AbstractAwaitStage<S> stage,
            WaitConfiguration configuration) {
        this(stage.source, configuration, stage.clock, stage.parker,
                stage.failureFactory);
    }

    public final S until(PreservingCondition<? super S> condition) {
        return complete(RuntimeCondition.preserving(
                Objects.requireNonNull(condition, "condition must not be null")));
    }

    public final S until(
            PreservingCondition.ExplainedCondition<? super S> condition) {
        return complete(RuntimeCondition.preserving(
                Objects.requireNonNull(condition, "condition must not be null")));
    }

    public final <R> R until(Condition<? super S, ? extends R> condition) {
        return complete(RuntimeCondition.<S, R>open(
                Objects.requireNonNull(condition, "condition must not be null")));
    }

    public final <R> R until(
            Condition.ExplainedCondition<? super S, ? extends R> condition) {
        return complete(RuntimeCondition.<S, R>open(
                Objects.requireNonNull(condition, "condition must not be null")));
    }

    protected final Source<S> source() {
        return source;
    }

    protected final WaitConfiguration configuration() {
        return configuration;
    }

    protected final <R> R complete(RuntimeCondition<S, R> condition) {
        configuration.validatePair();
        WaitEngine engine = new WaitEngine(configuration, clock, parker);
        return failureFactory.complete(
                engine.waitFor(source, condition),
                condition.description(), condition.explanation(), configuration);
    }
}
