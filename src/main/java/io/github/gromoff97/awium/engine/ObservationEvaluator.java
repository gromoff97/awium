package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNCONTROLLED;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
record ObservationEvaluator<S, R>(Source<? extends S> source,
        Function<? super S, ? extends Evaluation<? extends R>> evaluator,
        LongSupplier clock, long executionStarted) {

    AwaitAttempt<S, R> evaluate(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                restoreInterrupt();
            }
            return beforeObservation(phase, number, attemptStarted,
                    retrievalStarted, failure);
        }

        long observed = clock.getAsLong();
        if (interruptRaised()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterSourceInterruption(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, interruption);
        }

        Evaluation<? extends R> evaluation;
        try {
            evaluation = evaluator.apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                restoreInterrupt();
            }
            return afterConditionFailure(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, failure);
        }

        if ((evaluation == null || evaluation.status() != UNCONTROLLED)
                && interruptRaised()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterConditionFailure(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, interruption);
        }

        long completed = clock.getAsLong();
        AwaitAttempt.Timing.AfterObservation timing = afterObservation(attemptStarted,
                retrievalStarted, observed, completed);
        if (evaluation == null) {
            return new AwaitAttempt<>(number, phase,
                    new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(timing, actual,
                            new NullPointerException("condition returned null Evaluation"),
                            Evaluation.Context.Plain.INSTANCE));
        }

        AwaitAttempt.Outcome<S, R> outcome = switch (evaluation.status()) {
            case SATISFIED -> new AwaitAttempt.Outcome.Satisfied<>(timing, actual, evaluation.result());
            case UNSATISFIED -> new AwaitAttempt.Outcome.Unsatisfied<>(timing, actual,
                    evaluation.mismatch(), evaluation.assertionCause(), evaluation.context());
            case UNCONTROLLED -> uncontrolled(timing, actual, evaluation.uncontrolledCause(), evaluation.context());
        };
        return new AwaitAttempt<>(number, phase, outcome);
    }

    private AwaitAttempt<S, R> beforeObservation(AwaitAttempt.Phase phase,
            long number, long attemptStarted,
            long retrievalStarted, Throwable failure) {
        long completed = clock.getAsLong();
        var timing = new AwaitAttempt.Timing.BeforeObservation(offset(executionStarted, attemptStarted),
                offset(executionStarted, retrievalStarted), offset(executionStarted, completed));
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.SourceRetrievalFailed<>(timing, failure));
    }

    private AwaitAttempt<S, R> afterSourceInterruption(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted, long observed,
            S actual, InterruptedException interruption) {
        restoreInterrupt();
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.SourceInterrupted<>(timing, actual, interruption));
    }

    private AwaitAttempt<S, R> afterConditionFailure(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted, long observed,
            S actual, Throwable failure) {
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(timing, actual,
                        failure, Evaluation.Context.Plain.INSTANCE));
    }

    private static <S, R> AwaitAttempt.Outcome<S, R> uncontrolled(AwaitAttempt.Timing.AfterObservation timing,
            S actual, Throwable failure, Evaluation.Context context) {
        if (failure instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        if (failure instanceof InterruptedException) {
            restoreInterrupt();
        }
        return new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(timing, actual, failure, context);
    }

    private AwaitAttempt.Timing.AfterObservation afterObservation(long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new AwaitAttempt.Timing.AfterObservation(offset(executionStarted, attemptStarted),
                offset(executionStarted, retrievalStarted),
                offset(executionStarted, observed),
                offset(executionStarted, completed));
    }

    private static Duration offset(long executionStarted, long stageNanos) {
        return Duration.ofNanos(stageNanos - executionStarted);
    }

    private static void restoreInterrupt() {
        currentThread().interrupt();
    }

    private static boolean interruptRaised() {
        return currentThread().isInterrupted();
    }
}
