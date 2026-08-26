package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<S, E, F extends Source<?>, A> {

    private final Source<? extends S> source;
    private final WaitEngine engine;

    protected AbstractAwait(Source<? extends S> source) {
        this(source, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.engine = new WaitEngine(
                requireNonNull(configuration, "configuration must not be null"),
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    AbstractAwait(AbstractAwait<S, E, F, ?> await, WaitConfiguration configuration) {
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

    protected final Prepared<S, S> prepare(PreservingStage<? super S> condition) {
        return prepared(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    protected final <R> Prepared<S, R> prepare(
            ConditionStage<? super S, ? extends R> condition) {
        return prepared(ConditionRuntime.evaluator(condition), condition);
    }

    protected final Prepared<S, E> prepare(SelectedStage<? super S, F> condition) {
        return prepared(ConditionRuntime.selectedEvaluator(condition), condition);
    }

    protected final Prepared<S, List<E>> prepareSelectedSequence(
            SelectedSequenceStage<? super S, F> condition) {
        return prepared(ConditionRuntime.selectedSequenceEvaluator(condition), condition);
    }

    protected final <R> R complete(Prepared<S, R> condition) {
        return FailureFactory.complete(engine.waitFor(source, condition.evaluator()),
                condition::description, condition.explanation(), engine.configuration());
    }

    protected final <R> AwaitResult<S, R> capture(Prepared<S, R> condition) {
        return FailureFactory.capture(engine.recordedWaitFor(source, condition.evaluator()),
                condition::description, condition.explanation(), engine.configuration());
    }

    private <R> Prepared<S, R> prepared(Function<S, Evaluation<R>> evaluator,
            Object condition) {
        return new Prepared<>(evaluator, ConditionRuntime.description(condition),
                ConditionRuntime.explanation(condition));
    }

    protected record Prepared<S, R>(
            Function<? super S, ? extends Evaluation<? extends R>> evaluator,
            String description, String explanation) {

    }
}
