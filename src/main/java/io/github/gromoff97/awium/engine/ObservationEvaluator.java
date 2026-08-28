package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
record ObservationEvaluator<Observed, Result>(Source<? extends Observed> source,
        Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluator,
        LongSupplier clock, long executionStarted) {

    AwaitAttempt<Observed, Result> evaluate(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted) {
        Observed actual;
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

        ConditionEvaluation<? extends Result> evaluation;
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

        if (!(evaluation instanceof ConditionEvaluation.Uncontrolled<?>)
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
                            new NullPointerException("condition returned null ConditionEvaluation"),
                            ConditionEvaluation.Context.Plain.INSTANCE));
        }

        AwaitAttempt.Outcome<Observed, Result> outcome = switch (evaluation) {
            case ConditionEvaluation.Satisfied<? extends Result> satisfied ->
                    new AwaitAttempt.Outcome.Satisfied<>(timing, actual, satisfied.result());
            case ConditionEvaluation.Unsatisfied<?> unsatisfied ->
                    new AwaitAttempt.Outcome.Unsatisfied<>(timing, actual,
                            unsatisfied.mismatch(), null, unsatisfied.context());
            case ConditionEvaluation.AssertionUnsatisfied<?> unsatisfied ->
                    new AwaitAttempt.Outcome.Unsatisfied<>(timing, actual,
                            unsatisfied.mismatch(), unsatisfied.cause(), unsatisfied.context());
            case ConditionEvaluation.Uncontrolled<?> failure ->
                    uncontrolled(timing, actual, failure.cause(), failure.context());
        };
        return new AwaitAttempt<>(number, phase, outcome);
    }

    private AwaitAttempt<Observed, Result> beforeObservation(AwaitAttempt.Phase phase,
            long number, long attemptStarted,
            long retrievalStarted, Throwable failure) {
        long completed = clock.getAsLong();
        var timing = new AwaitAttempt.Timing.BeforeObservation(offset(executionStarted, attemptStarted),
                offset(executionStarted, retrievalStarted), offset(executionStarted, completed));
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.SourceRetrievalFailed<>(timing, failure));
    }

    private AwaitAttempt<Observed, Result> afterSourceInterruption(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted, long observed,
            Observed actual, InterruptedException interruption) {
        restoreInterrupt();
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.SourceInterrupted<>(timing, actual, interruption));
    }

    private AwaitAttempt<Observed, Result> afterConditionFailure(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted, long observed,
            Observed actual, Throwable failure) {
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(timing, actual,
                        failure, ConditionEvaluation.Context.Plain.INSTANCE));
    }

    private static <Observed, Result> AwaitAttempt.Outcome<Observed, Result> uncontrolled(AwaitAttempt.Timing.AfterObservation timing,
            Observed actual, Throwable failure, ConditionEvaluation.Context context) {
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
