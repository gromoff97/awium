package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
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
    private final WaitEngine engine;

    protected AbstractAwait(Source<? extends S> source) {
        this(source, defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    AbstractAwait(Source<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        this.source = requireNonNull(source, "source must not be null");
        this.engine = new WaitEngine(
                requireNonNull(configuration, "configuration must not be null"),
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

    public final A stableFor(Duration stability) {
        return reconfigured(engine.configuration().withStableFor(stability));
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

    protected final <R> R complete(CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            Supplier<String> description, String explanation) {
        return FailureFactory.complete(engine.waitFor(source, evaluator), description, explanation, engine.configuration());
    }

    protected static <R> Evaluation<R> replaceSatisfiedResult(Evaluation<?> evaluation, R satisfiedResult) {
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
        return complete(actual -> replaceSatisfiedResult(delegate.evaluate(actual), actual), delegate::description, explanation);
    }

    private <R> R complete(Condition<? super S, ? extends R> condition, String explanation) {
        return complete(condition::evaluate, condition::description, explanation);
    }
}
