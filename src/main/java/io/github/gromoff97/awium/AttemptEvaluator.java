package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.internal.engine.AttemptResult;
import io.github.gromoff97.awium.internal.engine.Interrupts;

import java.util.Objects;
import java.util.function.LongFunction;

final class AttemptEvaluator<S, R>
        implements LongFunction<AttemptResult<R>> {

    private final AwaitSources.Source<S> source;
    private final RuntimeCondition<S, R> condition;
    private final Interrupts interrupts;

    AttemptEvaluator(
            AwaitSources.Source<S> source,
            RuntimeCondition<S, R> condition,
            Interrupts interrupts) {
        this.source = Objects.requireNonNull(source);
        this.condition = Objects.requireNonNull(condition);
        this.interrupts = Objects.requireNonNull(interrupts);
    }

    @SuppressWarnings("removal")
    AttemptResult<R> evaluate(long attempt) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            return interrupts.fromThrown(
                    AttemptResult.Origin.SOURCE, interrupted, attempt);
        } catch (Throwable uncontrolled) {
            return AttemptResult.uncontrolled(
                    AttemptResult.Origin.SOURCE, uncontrolled, attempt);
        }

        AttemptResult<R> interrupted = interrupts.checkSource(attempt, actual);
        if (interrupted != null) {
            return interrupted;
        }

        Evaluation<R> evaluation;
        try {
            evaluation = condition.evaluate(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException conditionInterrupted) {
            return interrupts.fromThrown(AttemptResult.Origin.CONDITION,
                    conditionInterrupted, attempt, actual);
        } catch (Throwable uncontrolled) {
            return AttemptResult.uncontrolled(
                    AttemptResult.Origin.CONDITION,
                    uncontrolled, attempt, actual);
        }

        interrupted = interrupts.checkCondition(attempt, actual);
        if (interrupted != null) {
            return interrupted;
        }
        if (evaluation == null) {
            return AttemptResult.uncontrolled(
                    AttemptResult.Origin.CONDITION,
                    new NullPointerException("condition returned null Evaluation"),
                    attempt, actual);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> AttemptResult.satisfied(
                    actual, evaluation.result(), attempt);
            case UNSATISFIED -> AttemptResult.unsatisfied(
                    actual, evaluation.mismatch(), evaluation.assertionCause(), attempt);
            case UNCONTROLLED -> AttemptResult.uncontrolled(
                    AttemptResult.Origin.CONDITION,
                    evaluation.uncontrolledCause(), attempt, actual);
        };
    }

    @Override
    public AttemptResult<R> apply(long attempt) {
        return evaluate(attempt);
    }
}
