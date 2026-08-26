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
final class ObservationEvaluator {

    private final LongSupplier clock;

    ObservationEvaluator(LongSupplier clock) {
        this.clock = clock;
    }

    <S, R> EvaluatedAttempt<S, R> evaluate(Source<? extends S> source,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator,
            AwaitAttempt.Phase phase, long number, long executionStarted,
            long attemptStarted, long retrievalStarted) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            restoreInterrupt();
            return beforeObservation(phase, number, executionStarted,
                    attemptStarted, retrievalStarted, interrupted);
        } catch (Throwable failure) {
            return beforeObservation(phase, number, executionStarted,
                    attemptStarted, retrievalStarted, failure);
        }

        long observed = clock.getAsLong();
        if (interruptRaised()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterSourceInterruption(phase, number, executionStarted,
                    attemptStarted, retrievalStarted, observed, actual, interruption);
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
            return afterConditionFailure(phase, number, executionStarted,
                    attemptStarted, retrievalStarted, observed, actual, failure);
        }

        if ((evaluation == null || evaluation.status() != UNCONTROLLED)
                && interruptRaised()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterConditionFailure(phase, number, executionStarted,
                    attemptStarted, retrievalStarted, observed, actual, interruption);
        }

        long completed = clock.getAsLong();
        if (evaluation == null) {
            return evaluated(phase, number, completed,
                    new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(
                            afterObservation(executionStarted, attemptStarted,
                                    retrievalStarted, observed, completed),
                            actual, new NullPointerException("condition returned null Evaluation")));
        }

        AwaitAttempt.Outcome<S, R> outcome = switch (evaluation.status()) {
            case SATISFIED -> new AwaitAttempt.Outcome.Satisfied<>(
                    afterObservation(executionStarted, attemptStarted,
                            retrievalStarted, observed, completed),
                    actual, evaluation.result());
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? new AwaitAttempt.Outcome.Unsatisfied<>(
                            afterObservation(executionStarted, attemptStarted,
                                    retrievalStarted, observed, completed),
                            actual, evaluation.mismatch())
                    : new AwaitAttempt.Outcome.AssertionUnsatisfied<>(
                            afterObservation(executionStarted, attemptStarted,
                                    retrievalStarted, observed, completed),
                            actual, evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(executionStarted, attemptStarted,
                    retrievalStarted, observed, actual, evaluation.uncontrolledCause(), completed);
        };
        return evaluated(phase, number, completed, outcome);
    }

    private <S, R> EvaluatedAttempt<S, R> beforeObservation(AwaitAttempt.Phase phase,
            long number, long executionStarted, long attemptStarted,
            long retrievalStarted, Throwable failure) {
        long completed = clock.getAsLong();
        return evaluated(phase, number, completed,
                new AwaitAttempt.Outcome.SourceRetrievalFailed<>(
                        new AwaitAttempt.Timing.BeforeObservation(
                                offset(executionStarted, attemptStarted),
                                offset(executionStarted, retrievalStarted),
                                offset(executionStarted, completed)), failure));
    }

    private <S, R> EvaluatedAttempt<S, R> afterSourceInterruption(
            AwaitAttempt.Phase phase, long number, long executionStarted,
            long attemptStarted, long retrievalStarted, long observed,
            S actual, InterruptedException interruption) {
        restoreInterrupt();
        long completed = clock.getAsLong();
        return evaluated(phase, number, completed,
                new AwaitAttempt.Outcome.SourceInterrupted<>(
                        afterObservation(executionStarted, attemptStarted,
                                retrievalStarted, observed, completed), actual, interruption));
    }

    private <S, R> EvaluatedAttempt<S, R> afterConditionFailure(
            AwaitAttempt.Phase phase, long number, long executionStarted,
            long attemptStarted, long retrievalStarted, long observed,
            S actual, Throwable failure) {
        long completed = clock.getAsLong();
        return evaluated(phase, number, completed,
                new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(
                        afterObservation(executionStarted, attemptStarted,
                                retrievalStarted, observed, completed), actual, failure));
    }

    private static <S, R> AwaitAttempt.Outcome<S, R> uncontrolled(
            long executionStarted, long attemptStarted, long retrievalStarted,
            long observed, S actual, Throwable failure, long completed) {
        if (failure instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        if (failure instanceof InterruptedException) {
            restoreInterrupt();
        }
        return new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(
                afterObservation(executionStarted, attemptStarted,
                        retrievalStarted, observed, completed), actual, failure);
    }

    private static AwaitAttempt.Timing.AfterObservation afterObservation(
            long executionStarted, long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new AwaitAttempt.Timing.AfterObservation(
                offset(executionStarted, attemptStarted),
                offset(executionStarted, retrievalStarted),
                offset(executionStarted, observed),
                offset(executionStarted, completed));
    }

    private static <S, R> EvaluatedAttempt<S, R> evaluated(
            AwaitAttempt.Phase phase, long number, long completed,
            AwaitAttempt.Outcome<S, R> outcome) {
        return new EvaluatedAttempt<>(new AwaitAttempt<>(number, phase, outcome), completed);
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

    record EvaluatedAttempt<S, R>(AwaitAttempt<S, R> attempt, long completedNanos) {}
}
