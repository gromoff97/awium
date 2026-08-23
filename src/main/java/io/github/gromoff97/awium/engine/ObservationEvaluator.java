package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNCONTROLLED;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.CONDITION;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.SOURCE;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.AfterObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.BeforeObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Unsatisfied;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
final class ObservationEvaluator {

    private final LongSupplier clock;

    ObservationEvaluator(LongSupplier clock) {
        this.clock = clock;
    }

    <S, R> Attempt<R> evaluate(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            long number) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            return interruptedBefore(interrupted, number);
        } catch (Throwable uncontrolled) {
            return new BeforeObservation<>(SOURCE, uncontrolled, number, clock.getAsLong());
        }

        Uncontrolled<R> interrupted = interruptedAfter(SOURCE, number, actual);
        if (interrupted != null) {
            return interrupted;
        }

        Evaluation<? extends R> evaluation;
        try {
            evaluation = evaluator.apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException conditionInterrupted) {
            return interruptedAfter(CONDITION, conditionInterrupted, number, actual);
        } catch (Throwable uncontrolled) {
            return new AfterObservation<>(CONDITION, actual, uncontrolled, number, clock.getAsLong());
        }

        if (evaluation == null || evaluation.status() != UNCONTROLLED) {
            interrupted = interruptedAfter(CONDITION, number, actual);
            if (interrupted != null) {
                return interrupted;
            }
        }

        long completed = clock.getAsLong();
        if (evaluation == null) {
            return new AfterObservation<>(CONDITION, actual,
                    new NullPointerException("condition returned null Evaluation"), number, completed);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> new Satisfied<>(actual, evaluation.result(), number, completed);
            case UNSATISFIED -> new Unsatisfied<>(actual, evaluation.mismatch(),
                    evaluation.assertionCause(), number, completed);
            case UNCONTROLLED -> {
                Throwable cause = evaluation.uncontrolledCause();
                if (cause instanceof Error fatal
                        && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
                    throw fatal;
                }
                yield cause instanceof InterruptedException interruption
                        ? interruptedAfter(CONDITION, interruption, number, actual)
                        : new AfterObservation<>(CONDITION, actual, cause, number, completed);
            }
        };
    }

    private <R> Uncontrolled<R> interruptedBefore(InterruptedException interrupted, long number) {
        restoreInterrupt();
        return new BeforeObservation<>(SOURCE, interrupted, number, clock.getAsLong());
    }

    private <R> Uncontrolled<R> interruptedAfter(Origin origin, long number, Object actual) {
        return currentThread().isInterrupted()
                ? interruptedAfter(origin,
                        new InterruptedException("caller thread interrupt flag was set"), number, actual)
                : null;
    }

    private <R> Uncontrolled<R> interruptedAfter(Origin origin,
            InterruptedException interrupted, long number, Object actual) {
        restoreInterrupt();
        return new AfterObservation<>(origin, actual, interrupted, number, clock.getAsLong());
    }

    private static void restoreInterrupt() {
        currentThread().interrupt();
    }
}
