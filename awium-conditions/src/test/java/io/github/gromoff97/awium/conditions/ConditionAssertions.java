package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

final class ConditionAssertions {

    static <INPUT> ConditionResult<?> probe(ConditionRun<INPUT, ?> run, INPUT actual) {
        run.beginAttempt();
        return assertDoesNotThrow(() -> run.probe(actual));
    }

    static ConditionResult<?> evaluate(AwaitCondition condition, Object actual) {
        return assertDoesNotThrow(() -> ConditionExecution.bind(condition).probe(actual));
    }

    static Unsatisfied<?> rejection(AwaitCondition condition, Object actual) {
        return assertInstanceOf(Unsatisfied.class, evaluate(condition, actual));
    }

    static <INPUT> Unsatisfied<?> rejection(AwaitCondition.InputCondition<? super INPUT> condition, INPUT actual) {
        return rejection((AwaitCondition) condition, actual);
    }

    static Throwable thrown(AwaitCondition condition, Object actual) {
        var failure = assertThrows(Throwable.class, () -> ConditionExecution.bind(condition).probe(actual));
        return failure instanceof ProbeFailure nested ? nested.getCause() : failure;
    }

    static Condition<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new DefinedCondition<>(description, reference, () -> ConditionResult::satisfied);
    }

    static <SOURCE, RESULT> ConditionResult<? extends RESULT> evaluate(Condition<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionExecution.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionExecution.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertInstanceOf(Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    static String mismatch(ConditionResult<?> evaluation) {
        return assertInstanceOf(Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionAssertions() {}

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

    static Throwable assertFailure(Reason reason, Executable executable) {
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
