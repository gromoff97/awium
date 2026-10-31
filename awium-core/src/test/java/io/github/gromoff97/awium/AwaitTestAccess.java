package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.TestConditions.expected;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static <INPUT, RESULT> ConditionRun<INPUT, RESULT> start(AwaitCondition condition) {
        return ConditionExecution.start(condition, false);
    }

    static AwaitFailure failure(AwaitResult<?, ?> result) {
        return assertInstanceOf(AwaitResult.Failed.class, result).failure();
    }

    static AwaitFailure failure(Reason expected, AwaitResult<?, ?> result) {
        var failure = failure(result);
        assertEquals(expected, failure.reason());
        return failure;
    }

    /** Flattens condition histories for tests of the complete execution timeline. */
    static <OBSERVED> List<Attempt<OBSERVED>> attempts(AwaitResult<OBSERVED, ?> result) {
        return result.conditions().stream().flatMap(history -> history.attempts().values().stream()).toList();
    }

    static <OBSERVED> OBSERVED observation(Attempt<OBSERVED> attempt) {
        return switch (attempt) {
            case Attempt.SatisfiedObservation<OBSERVED> satisfied -> satisfied.observed();
            case Attempt.UnsatisfiedObservation<OBSERVED> unsatisfied -> unsatisfied.observed();
            case Attempt.FailedAfterObservation<OBSERVED> failed -> failed.observed();
            case Attempt.FailedBeforeObservation<OBSERVED> ignored -> fail("expected an observation");
        };
    }

    static Attempt.Failure<?> uncontrolled(Attempt.Operation operation, Attempt<?> result) {
        var failure = assertInstanceOf(Attempt.Failure.class, result);
        assertEquals(operation, failure.operation());
        return failure;
    }

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence,
                WaitConfiguration.DEFAULT.clock(), WaitConfiguration.DEFAULT.parker());
    }

    static <S> ValueAwait<S> timedAwait(Callable<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    static <S> DiagnosticAwait<S> timedTryAwait(Callable<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(source, configuration.withTime(clock, parker));
    }

    static <OBSERVED> DiagnosticAwait<OBSERVED> diagnostic(ValueAwait<OBSERVED> waiting) {
        return new DiagnosticAwait<>(waiting.source, waiting.configuration);
    }
}
