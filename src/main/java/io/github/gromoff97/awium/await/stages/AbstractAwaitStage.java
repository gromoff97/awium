package io.github.gromoff97.awium.await.stages;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;

public abstract class AbstractAwaitStage<S> {

    private static final FailureFactory FAILURE_FACTORY = new FailureFactory();

    private final Source<S> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;

    protected AbstractAwaitStage(Source<S> source) {
        this(source, defaults(), System::nanoTime,
                LockSupport::parkNanos);
    }

    protected AbstractAwaitStage(Source<S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        this.source = requireNonNull(source);
        this.configuration = requireNonNull(configuration);
        this.clock = requireNonNull(clock);
        this.parker = requireNonNull(parker);
    }

    protected AbstractAwaitStage(AbstractAwaitStage<S> stage,
            WaitConfiguration configuration) {
        this(stage.source, configuration, stage.clock, stage.parker);
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

    protected final Source<S> source() {
        return source;
    }

    protected final WaitConfiguration configuration() {
        return configuration;
    }

    protected final <R> R complete(RuntimeCondition<S, R> condition) {
        configuration.validatePair();
        WaitEngine engine = new WaitEngine(configuration, clock, parker);
        return FAILURE_FACTORY.complete(
                engine.waitFor(source, condition), condition, configuration);
    }
}
