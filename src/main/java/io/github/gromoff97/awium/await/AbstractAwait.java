package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<S, E, A> {

    private final Source<? extends S> source;
    private final CheckedFunction<? super S, ? extends E> selector;
    private final WaitEngine engine;

    protected AbstractAwait(Source<? extends S> source,
            CheckedFunction<? super S, ? extends E> selector) {
        this(source, selector, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source,
            CheckedFunction<? super S, ? extends E> selector,
            WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.selector = requireNonNull(selector, "selector must not be null");
        this.engine = new WaitEngine(
                requireNonNull(configuration, "configuration must not be null"),
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    AbstractAwait(AbstractAwait<S, E, ?> await, WaitConfiguration configuration) {
        this(await.source, await.selector, configuration,
                await.engine.clock(), await.engine.parker());
    }

    public final A every(Duration interval) {
        return reconfigured(engine.configuration().withEvery(interval));
    }

    public final A upTo(Duration timeout) {
        return reconfigured(engine.configuration().withUpTo(timeout));
    }

    public final A stableFor(Duration stability) {
        return reconfigured(engine.configuration().withStableFor(stability));
    }

    abstract A reconfigured(WaitConfiguration configuration);

    protected final Prepared<S, S> prepare(PreservingCondition<? super S> condition) {
        return prepareSelected(requireNonNull(condition, "condition must not be null").delegate(),
                actual -> actual, null);
    }

    protected final Prepared<S, S> prepare(
            PreservingCondition.ExplainedCondition<? super S> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return prepareSelected(explained.delegate().delegate(),
                actual -> actual, explained.explanation());
    }

    protected final <R> Prepared<S, R> prepare(
            Condition<? super S, ? extends R> condition) {
        return prepare(requireNonNull(condition, "condition must not be null"), null);
    }

    protected final <R> Prepared<S, R> prepare(
            Condition.ExplainedCondition<? super S, ? extends R> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return prepare(explained.delegate(), explained.explanation());
    }

    protected final Prepared<S, E> prepare(SelectedCondition<? super S> condition) {
        return prepareSelected(requireNonNull(condition, "condition must not be null").delegate(),
                selector, null);
    }

    protected final Prepared<S, E> prepare(
            SelectedCondition.ExplainedCondition<? super S> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return prepareSelected(explained.delegate().delegate(),
                selector, explained.explanation());
    }

    protected final <R> R complete(Prepared<S, R> condition) {
        return FailureFactory.complete(engine.waitFor(source, condition.evaluator()),
                condition.description(), condition.explanation(), engine.configuration());
    }

    private <R> Prepared<S, R> prepare(Condition<? super S, ? extends R> condition,
            String explanation) {
        return new Prepared<>(condition::evaluate, condition::description, explanation);
    }

    private <R> Prepared<S, R> prepareSelected(Condition<? super S, ?> condition,
            CheckedFunction<? super S, ? extends R> selection, String explanation) {
        return new Prepared<>(actual -> {
            Evaluation<?> evaluation = condition.evaluate(actual);
            return evaluation == null ? null : evaluation.continueIfSatisfied(
                    ignored -> satisfied(selection.apply(actual)));
        }, condition::description, explanation);
    }

    protected record Prepared<S, R>(
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            Supplier<String> description, String explanation) {

    }
}
