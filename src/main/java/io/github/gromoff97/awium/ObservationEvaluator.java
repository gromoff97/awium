package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
record ObservationEvaluator<Observed, Result>(Source<? extends Observed> source,
        Function<? super Observed, ? extends ConditionResult<? extends Result>> evaluator,
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
                currentThread().interrupt();
            }
            return beforeObservation(phase, number, attemptStarted,
                    retrievalStarted, failure);
        }

        long observed = clock.getAsLong();
        if (currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterSourceInterruption(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, interruption);
        }

        ConditionResult<? extends Result> evaluation;
        try {
            evaluation = evaluator.apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                currentThread().interrupt();
            }
            return afterConditionFailure(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, failure,
                    ConditionResult.Context.Plain.INSTANCE);
        }

        if (!(evaluation instanceof ConditionResult.Uncontrolled<?>)
                && currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return afterConditionFailure(phase, number, attemptStarted,
                    retrievalStarted, observed, actual, interruption,
                    evaluation == null ? ConditionResult.Context.Plain.INSTANCE : evaluation.context());
        }

        long completed = clock.getAsLong();
        AwaitAttempt.Timing.AfterObservation timing = afterObservation(attemptStarted,
                retrievalStarted, observed, completed);
        if (evaluation == null) {
            evaluation = ConditionResult.uncontrolled(
                    new NullPointerException("condition returned null ConditionResult"));
        }
        if (evaluation instanceof ConditionResult.Uncontrolled<?> uncontrolled) {
            Throwable failure = uncontrolled.cause();
            if (failure instanceof Error fatal
                    && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
                throw fatal;
            }
            if (failure instanceof InterruptedException) {
                currentThread().interrupt();
            }
        }
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.Evaluated<>(timing, actual, evaluation));
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
        currentThread().interrupt();
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.SourceInterrupted<>(timing, actual, interruption));
    }

    private AwaitAttempt<Observed, Result> afterConditionFailure(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted, long observed,
            Observed actual, Throwable failure, ConditionResult.Context context) {
        long completed = clock.getAsLong();
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.Evaluated<>(timing, actual,
                        new ConditionResult.Uncontrolled<>(failure, context)));
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

}
