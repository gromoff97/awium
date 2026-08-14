package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;

abstract class AbstractAwait<S, A> {

    private final Source<? extends S> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;

    protected AbstractAwait(Source<? extends S> source) {
        this(source, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        this.clock = requireNonNull(clock, "clock must not be null");
        this.parker = requireNonNull(parker, "parker must not be null");
    }

    AbstractAwait(AbstractAwait<S, ?> await, WaitConfiguration configuration) {
        this(await.source, configuration, await.clock, await.parker);
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
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public final S until(PreservingCondition.ExplainedCondition<? super S> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    public final <R> R until(Condition<? super S, ? extends R> condition) {
        return complete(requireNonNull(condition, "condition must not be null"), null);
    }

    public final <R> R until(Condition.ExplainedCondition<? super S, ? extends R> condition) {
        var explained = requireNonNull(condition, "condition must not be null");
        return complete(explained.delegate(), explained.explanation());
    }

    abstract A reconfigured(WaitConfiguration configuration);

    protected final <R> R complete(CheckedFunction<? super S, Evaluation<R>> evaluator, Supplier<String> description, String explanation) {
        return FailureFactory.complete(new WaitEngine(configuration, clock, parker).waitFor(source, evaluator), description, explanation, configuration);
    }

    protected static <R> Evaluation<R> withResult(Evaluation<?> evaluation, R satisfiedResult) {
        if (evaluation == null) {
            return null;
        }
        return switch (evaluation.status()) {
            case SATISFIED -> satisfied(satisfiedResult);
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? unsatisfied(evaluation.mismatch())
                    : assertionUnsatisfied(evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(evaluation.uncontrolledCause());
        };
    }

    private S complete(PreservingCondition<? super S> condition, String explanation) {
        Condition<? super S, ?> delegate = condition.delegate();
        return complete(actual -> withResult(delegate.evaluate(actual), actual), delegate::description, explanation);
    }

    private <R> R complete(Condition<? super S, ? extends R> condition, String explanation) {
        return complete(actual -> evaluate(condition, actual), condition::description, explanation);
    }

    @SuppressWarnings("unchecked")
    private static <S, R> Evaluation<R> evaluate(Condition<? super S, ? extends R> condition, S actual) throws Exception {
        return (Evaluation<R>) condition.evaluate(actual);
    }
}
