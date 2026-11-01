package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public final class AwaitAssertions {
    private AwaitAssertions() {}

    public static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("expected a satisfied condition, got " + evaluation, evaluation.cause());
    }

    public static AwaitFailure failure(AwaitResult<?, ?> result) {
        return assertInstanceOf(AwaitResult.Failed.class, result).failure();
    }

    public static AwaitFailure failure(Reason expected, AwaitResult<?, ?> result) {
        var failure = failure(result);
        assertEquals(expected, failure.reason());
        return failure;
    }

    public static <OBSERVED> List<Attempt<OBSERVED>> attempts(AwaitResult<OBSERVED, ?> result) {
        return result.conditions().stream().flatMap(history -> history.attempts().values().stream()).toList();
    }

    public static <OBSERVED> OBSERVED observation(Attempt<OBSERVED> attempt) {
        return switch (attempt) {
            case Attempt.SatisfiedObservation<OBSERVED> satisfied -> satisfied.observed();
            case Attempt.UnsatisfiedObservation<OBSERVED> unsatisfied -> unsatisfied.observed();
            case Attempt.FailedAfterObservation<OBSERVED> failed -> failed.observed();
            case Attempt.FailedBeforeObservation<OBSERVED> failed -> fail("expected an observation, got " + failed, failed.cause());
        };
    }

    public static Attempt.Failure<?> uncontrolled(Attempt.Operation operation, Attempt<?> result) {
        var failure = assertInstanceOf(Attempt.Failure.class, result);
        assertEquals(operation, failure.operation());
        return failure;
    }

    public static Throwable assertFailure(Reason reason, Executable executable) {
        Throwable thrown = switch (reason) {
            case TIMEOUT, PERSISTENCE_FAILED -> assertThrows(AwaitAssertionError.class, executable);
            default -> assertThrows(AwaitExecutionException.class, executable);
        };
        AwaitFailure failure = thrown instanceof AwaitAssertionError assertion
                ? assertion.failure() : ((AwaitExecutionException) thrown).failure();
        assertEquals(reason, failure.reason());
        return thrown;
    }
}
