package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.ExecutionStep;
import io.github.gromoff97.awium.internal.FailureDiagnostics;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason.values;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.Conditions.asserted;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Operation.WAITING;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class FailureTaxonomyTest {

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

    @Test
    void diagnosticFailureRetainsBothOriginalCausesWithoutMutatingThem() {
        var time = new FakeTime(0);
        var original = new AssertionError("not ready");
        var rendering = new IllegalStateException("formatting failed");
        var actual = new Object() {
            @Override public String toString() { throw rendering; }
        };
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var check = asserted(value -> { throw original; });

        var captured = failure(TIMEOUT, diagnostic(waiting).until(check));
        assertSame(original, captured.cause());
        assertSame(rendering, captured.suppressed().getFirst());
        var thrown = assertInstanceOf(AwaitAssertionError.class,
                assertFailure(TIMEOUT, () -> waiting.until(check)));
        assertSame(original, thrown.failure().cause());
        assertSame(rendering, thrown.failure().suppressed().getFirst());
        assertEquals(thrown.failure().message(), thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertArrayEquals(new Throwable[]{rendering}, thrown.getSuppressed());
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, rendering.getSuppressed().length);
    }

    @ParameterizedTest
    @MethodSource("failureCases")
    void renderingFailurePreservesTheOriginalCategory(
            Reason reason, Throwable original, Phase phase, Attempt<Object> retained, Function<ExecutionStep<Object>, WaitCompletion> completion) {
        var rendering = new IllegalStateException("formatting failed");
        Object expected = new Object() {
            @Override public String toString() { throw rendering; }
        };
        var metadata = diagnosticCondition("ready", new Reference<>("Expected", expected));
        var attempt = new ExecutionStep<>(metadata, 1, 0, phase, 2, retained);
        var outcome = completion.apply(attempt);
        var configuration = config(1, 2, 0);
        var captured = FailureDiagnostics.failure(outcome, configuration, 1);
        assertEquals(reason, captured.reason());
        assertSame(original, captured.cause());
        assertEquals(List.of(rendering), captured.suppressed());
        var report = new Failed<Object, String>(List.of(new ConditionHistory<>(metadata,
                new TreeMap<>(Map.of(1L, attempt.attempt())))), captured);
        var reported = assertFailure(reason, report::orThrow);
        assertSame(captured, reported instanceof AwaitAssertionError assertion
                ? assertion.failure() : ((AwaitExecutionException) reported).failure());
        assertSame(original, reported.getCause());
        assertArrayEquals(new Throwable[]{rendering}, reported.getSuppressed());
        assertNotSame(reported, assertFailure(reason, report::orThrow));
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, rendering.getSuppressed().length);
    }

    @AfterEach
    void diagnosticsLeaveTheThreadUninterrupted() {
        assertFalse(interrupted(), "diagnostics must not interrupt the calling thread");
    }

    @Test
    void causeThatFailsToDescribeItselfIsNotDuplicatedAsSuppressed() {
        var original = new IllegalStateException() {
            @Override public String getMessage() { throw this; }
        };
        var failure = assertFailure(CONDITION_FAILED,
                () -> await(() -> "actual").until(condition("ready", value -> { throw original; })));
        assertSame(original, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    void structuredFailureDefensivelyCopiesSuppressedCauses() {
        var original = new AssertionError("original");
        var causes = new ArrayList<Throwable>(List.of(original));
        var failure = new AwaitFailure(TIMEOUT, "not ready", null, causes);
        causes.clear();
        assertEquals(List.of(original), failure.suppressed());
        assertThrows(UnsupportedOperationException.class, () -> failure.suppressed().clear());
    }

    @Test
    void structuredFailureAllowsNoCause() {
        assertNull(new AwaitFailure(TIMEOUT, "not ready", null, List.of()).cause());
    }
    @ParameterizedTest
    @CsvSource({", message", "TIMEOUT,"})
    void structuredFailuresRejectMissingRequiredFields(Reason reason, String message) {
        assertThrows(NullPointerException.class, () -> new AwaitFailure(reason, message, null, List.of()));
    }

    private static List<Arguments> failureCases() {
        var timeout = new AssertionError("not ready");
        var persistence = new AssertionError("not ready");
        var source = new IllegalStateException("operation failed");
        var condition = new IllegalStateException("operation failed");
        var interruption = new InterruptedException("interrupted");
        var waiting = new IllegalStateException("operation failed");
        var cases = List.of(
                failureCase(TIMEOUT, timeout, ACQUISITION, new Unsatisfied<>("actual", "not ready", timeout), step -> new Timeout(step.completedAfterNanos(), step)),
                failureCase(PERSISTENCE_FAILED, persistence, PERSISTENCE, new Unsatisfied<>("actual", "not ready", persistence),
                        step -> new PersistenceFailure(0, step)),
                failureCase(SOURCE_FAILED, source, ACQUISITION, new FailedBeforeObservation<>(SOURCE, source),
                        step -> new Uncontrolled(step, false)),
                failureCase(CONDITION_FAILED, condition, ACQUISITION, new FailedAfterObservation<>("actual", CONDITION, condition, null),
                        step -> new Uncontrolled(step, false)),
                failureCase(INTERRUPTED, interruption, ACQUISITION, new FailedAfterObservation<>("actual", CONDITION, interruption, null),
                        step -> new Uncontrolled(step, true)),
                failureCase(WAIT_FAILED, waiting, ACQUISITION, new FailedBeforeObservation<>(WAITING, waiting),
                        step -> new Uncontrolled(step, false)));
        assertThat(cases).extracting(arguments -> arguments.get()[0]).containsExactlyElementsOf(List.of(values()));
        return cases;
    }

    private static Arguments failureCase(Reason reason, Throwable cause, Phase phase, Attempt<Object> attempt,
            Function<ExecutionStep<Object>, WaitCompletion> completion) {
        return argumentSet(reason.name(), reason, cause, phase, attempt, completion);
    }

}
