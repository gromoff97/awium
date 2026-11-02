package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Operation.WAITING;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.values;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.assertFailure;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.TestConditions.diagnosticCondition;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class FailureDiagnosticsTest {

    @Test
    void aCustomRunWithoutHistoriesStillExplainsItsTerminalAttempt() {
        var original = new IllegalStateException("receipt is unavailable");
        var condition = new Condition<String, String>() {
            @Override public String description() { return "receipt"; }
            @Override public AttemptResult<String> probe(String actual) { throw original; }
            @Override public List<AwaitCondition> historyConditions(AwaitCondition definition) { return List.of(); }
        };

        var result = tryAwait(() -> "order").until(condition);

        assertThat(result.conditions()).isEmpty();
        assertSame(original, failure(result).cause());
        assertThat(result.toString()).contains("Condition: receipt", "Last attempt #1: failed (CONDITION)",
                "Observed now: \"order\"", "Cause: java.lang.IllegalStateException: receipt is unavailable");
    }

    @Test
    void aRunCanAddFailureDetailsWithoutRepeatingItsProbe() {
        var run = describedRun(() -> new Diagnostic(null, new Reference<>("Detail", "custom")));
        var attempt = new UnsatisfiedObservation<Object>("actual", "not ready", null);
        run.record(1, attempt);
        var outcome = new Timeout(new ExecutionStep<>(ACQUISITION, 2, attempt));

        var report = failure(run.finish(outcome, config(1, 2, 0)));

        assertThat(report.message()).contains("Attempt: 1 (ACQUISITION)", "Condition 1: ready",
                "Expected: \"ready\"", "Observed now: \"actual\"", "Mismatch: not ready", "Timing:")
                .containsOnlyOnce("Detail: \"custom\"");
    }

    @ParameterizedTest
    @MethodSource("failureCases")
    void aBrokenFailureHookPreservesTheOriginalCategory(
            Reason reason, Throwable original, Phase phase, Attempt<Object> retained,
            Function<ExecutionStep<Object>, WaitCompletion> completion) {
        var rendering = new AssertionError("custom diagnostics failed");
        var run = describedRun(() -> { throw rendering; });
        run.record(1, retained);
        var outcome = completion.apply(new ExecutionStep<>(phase, 2, retained));

        var report = failure(run.finish(outcome, config(1, 2, 0)));

        assertEquals(reason, report.reason());
        assertSame(original, report.cause());
        assertThat(report.suppressed()).containsExactly(rendering);
        assertThat(report.message()).contains("Attempt: 1 (" + phase + ")", "Timing:",
                "Cause:", "Suppressed: java.lang.AssertionError");
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    void brokenUserMetadataDoesNotHideTheProbeFailure() {
        var probeFailure = new AssertionError("probe failed");
        var renderingFailure = new IllegalStateException("description failed");
        var condition = new Condition<String, String>() {
            @Override public String description() { throw renderingFailure; }
            @Override public AttemptResult<String> probe(String actual) { throw probeFailure; }
        };
        var result = tryAwait(() -> "ready").until(condition);
        assertSame(probeFailure, failure(result).cause());
        assertThat(failure(result).suppressed()).containsExactly(renderingFailure);
        assertThat(result.toString()).contains("Condition 1: <unprintable>", "probe failed");
    }

    @Test
    void locatedFailuresRetainTheirCauseAndObservation() {
        var cause = new IllegalStateException("offline");
        var result = tryAwait(() -> "actual").until(check("receipt", actual -> {
            throw locatedFailure("receipt", cause);
        }));

        var failed = assertInstanceOf(FailedAfterObservation.class, attempts(result).getFirst());
        assertEquals("actual", failed.observed());
        assertEquals("receipt", failed.location());
        assertSame(cause, failed.cause());
        var failure = failure(CONDITION_FAILED, result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("Within: receipt");
        assertThat(failed.toString()).contains("Within: receipt");
    }

    @Test
    void aLocatedFailureWithoutACauseIsRejectedInsideTheCondition() {
        var result = tryAwait(() -> "actual").until(check("receipt", actual -> {
            throw locatedFailure("receipt", null);
        }));

        var cause = failure(CONDITION_FAILED, result).cause();
        assertInstanceOf(NullPointerException.class, cause);
        assertEquals("cause must not be null", cause.getMessage());
    }

    @Test
    void diagnosticFailureRetainsBothOriginalCausesWithoutMutatingThem() {
        var time = new FakeTime(0);
        var original = new AssertionError("not ready");
        var rendering = new IllegalStateException("formatting failed");
        var actual = new Object() {
            @Override public String toString() { throw rendering; }
        };
        var waiting = withTime(await(() -> actual), time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var check = check("not ready", value -> unsatisfied("not ready", original));

        var returned = failure(TIMEOUT, diagnostic(waiting).until(check));
        assertSame(original, returned.cause());
        assertSame(rendering, returned.suppressed().getFirst());
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
        var attempt = new ExecutionStep<>(phase, 2, retained);
        var outcome = completion.apply(attempt);
        var configuration = config(1, 2, 0);
        var run = new ConditionRun<Object, Object>(metadata);
        run.initialize();
        run.record(1, retained);
        var report = run.finish(outcome, configuration);
        var returned = failure(report);
        assertEquals(reason, returned.reason());
        assertSame(original, returned.cause());
        assertEquals(List.of(rendering), returned.suppressed());
        var reported = assertFailure(reason, report::orThrow);
        assertSame(returned, reported instanceof AwaitAssertionError assertion
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
                () -> await(() -> "actual").until(check("ready", value -> { throw original; })));
        assertSame(original, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(0, original.getSuppressed().length);
    }

    private static ConditionRun<Object, Object> describedRun(Supplier<Diagnostic> diagnostic) {
        var run = new ConditionRun<>(new Condition<Object, Object>() {
            @Override public String description() { return "ready"; }
            @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", "ready")); }
            @Override public AttemptResult<Object> probe(Object actual) { throw new AssertionError("diagnostics must not probe"); }
            @Override public AttemptResult<?> historyResult(AttemptResult<?> attempt) { throw new AssertionError("diagnostics must not project history"); }
            @Override public Diagnostic diagnostics(Attempt<?> attempt) { return diagnostic.get(); }
        });
        run.initialize();
        return run;
    }

    private static List<Arguments> failureCases() {
        var timeout = new AssertionError("not ready");
        var persistence = new AssertionError("not ready");
        var source = new IllegalStateException("operation failed");
        var condition = new IllegalStateException("operation failed");
        var interruption = new InterruptedException("interrupted");
        var waiting = new IllegalStateException("operation failed");
        var cases = List.of(
                failureCase(TIMEOUT, timeout, ACQUISITION, new UnsatisfiedObservation<>("actual", "not ready", timeout), Timeout::new),
                failureCase(PERSISTENCE_FAILED, persistence, PERSISTENCE, new UnsatisfiedObservation<>("actual", "not ready", persistence),
                        step -> new PersistenceFailure(0, step)),
                failureCase(SOURCE_FAILED, source, ACQUISITION, new FailedBeforeObservation<>(SOURCE, source),
                        step -> new Uncontrolled(step, false)),
                failureCase(CONDITION_FAILED, condition, ACQUISITION, new FailedAfterObservation<>("actual", CONDITION, condition, null),
                        step -> new Uncontrolled(step, false)),
                failureCase(INTERRUPTED, interruption, ACQUISITION, new FailedAfterObservation<>("actual", CONDITION, interruption, null),
                        step -> new Uncontrolled(step, true)),
                failureCase(WAIT_FAILED, waiting, ACQUISITION, new FailedBeforeObservation<>(WAITING, waiting),
                        step -> new Uncontrolled(step, false)));
        assertThat(cases).extracting(arguments -> arguments.get()[0]).containsExactlyInAnyOrderElementsOf(List.of(values()));
        return cases;
    }

    private static Arguments failureCase(Reason reason, Throwable cause, Phase phase, Attempt<Object> attempt,
            Function<ExecutionStep<Object>, WaitCompletion> completion) {
        return argumentSet(reason.name(), reason, cause, phase, attempt, completion);
    }
}
