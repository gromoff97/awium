package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.asserted;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TryAwaitFailureTest {

    private final FakeTime time = new FakeTime(0);

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest(name = "clock interrupt={0}, callback throws={1}")
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void interruptionAfterEvaluationRetainsTheOriginalCauseAndNestedLocation(boolean completionClock, boolean thrown) {
        boolean[] evaluated = {false};
        var cause = new IllegalStateException("not ready");
        var check = OptionalConditions.hasValue(condition("ready", (String actual) -> {
            evaluated[0] = true;
            if (!completionClock) currentThread().interrupt();
            if (thrown) throw cause;
            return unsatisfied("not ready", cause);
        }));
        var result = tryAwait(() -> Optional.of("actual")).usingTime(() -> {
            if (evaluated[0]) currentThread().interrupt();
            return 0;
        }, nanos -> {}).until(check);
        var failure = failure(result);
        var attempt = uncontrolled(Attempt.Operation.CONDITION, attempts(result).getFirst());
        assertEquals(INTERRUPTED, failure.reason());
        assertSame(cause, failure.cause());
        assertSame(cause, attempt.cause());
        assertEquals(thrown || !completionClock ? "optional value" : null, attempt.location());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void singlePredicateAssertionStopsBothTerminalsWithTheOriginalCause() {
        var assertion = new AssertionError("predicate failed");
        int[] calls = {0};
        Predicate<String> predicate = actual -> {
            calls[0]++;
            throw assertion;
        };

        var result = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(predicate::test));

        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertSame(assertion, result.failure().cause());
        assertEquals(1, attempts(result).size());
        assertEquals(2, calls[0]);
    }

    @Test
    void capturesAcquisitionTimeoutAndPersistenceFailureWithoutChangingThem() {
        var timeout = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> false));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryAwaitFailureTest::unstableCondition);

        assertInstanceOf(Attempt.Unsatisfied.class,
                attempts(timeout).getLast());
        assertInstanceOf(Attempt.Unsatisfied.class,
                attempts(persistence).getLast());
    }

    @Test
    void capturesSourceAndConditionFailuresWithoutChangingThem() {
        var source = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        var sourceAssertion = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> {
                    throw new IllegalStateException("condition failed");
                }));
        var conditionAssertion = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> {
                    throw new AssertionError("condition assertion");
                }));

        uncontrolled(Attempt.Operation.SOURCE, attempts(source).getLast());
        uncontrolled(Attempt.Operation.SOURCE, attempts(sourceAssertion).getLast());
        uncontrolled(Attempt.Operation.CONDITION, attempts(condition).getLast());
        uncontrolled(Attempt.Operation.CONDITION, attempts(conditionAssertion).getLast());
    }

    @Test
    void capturesConditionFactoryFailuresInsideTheFirstAttempt() {
        var failure = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> {
                    throw new IllegalStateException("condition factory failed");
                }));
        var nullEvaluator = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> null));

        assertEquals(1, attempts(failure).size());
        uncontrolled(Attempt.Operation.CONDITION, attempts(failure).getFirst());
        assertEquals("evaluator must not be null", nullEvaluator.failure().cause().getMessage());
    }

    @Test
    void assertedFailuresRemainControlled() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> asserted(actual -> { throw new AssertionError("assertion failed"); }));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryAwaitFailureTest::unstableAssertion);

        assertInstanceOf(Attempt.Unsatisfied.class,
                attempts(timeout).getLast());
        assertInstanceOf(Attempt.Unsatisfied.class,
                attempts(persistence).getLast());
    }

    @Test
    void capturesParkingAndEveryInterruptionBoundary() {
        var acquisitionParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); }),
                () -> condition("never", actual -> unsatisfied("not ready")));
        var persistenceParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 2), time,
                        nanos -> { throw new IllegalStateException("park failed"); }),
                () -> isNotNull);

        assertEquals(2, attempts(acquisitionParking).size());
        assertEquals(2, attempts(persistenceParking).size());
        uncontrolled(Attempt.Operation.WAITING, attempts(acquisitionParking).getLast());
        uncontrolled(Attempt.Operation.WAITING, attempts(persistenceParking).getLast());
        var beforeSource = assertParity(time -> {
                    currentThread().interrupt();
                    return timedAwait(() -> "actual", config(1, 2, 0), time, time);
                }, () -> isNotNull);
        assertThat(beforeSource.failure().message()).startsWith("Waiting interrupted");
        var thrownSourceInterruption = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertEquals(SOURCE_FAILED, thrownSourceInterruption.failure().reason());
        assertThat(thrownSourceInterruption.failure().message()).startsWith("Source retrieval failed");
        var sourceFlag = assertParity(time -> timedAwait((Callable<String>) () -> {
                    time.advanceNanos(7);
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertThat(sourceFlag.failure().message()).startsWith("Source retrieval interrupted");
        assertThat(sourceFlag.failure().message()).contains("elapsed 7 ns", "Actual: \"actual\"");
        var condition = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> {
                    currentThread().interrupt();
                    return true;
                }));

        uncontrolled(Attempt.Operation.CONDITION, attempts(condition).getLast());
        assertThat(condition.failure().message()).startsWith("Condition evaluation interrupted");
        var interruptTime = new FakeTime(0);
        failed(timedTryAwait(() -> "actual", config(1, 2, 0),
                interruptTime, interruptTime).until(condition("condition succeeds", actual -> {
                    currentThread().interrupt();
                    return satisfied();
                })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluationAndDiagnosticRenderingFailure() {
        var nullEvaluation = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> null));
        var diagnostics = assertParity(time -> timedAwait(
                        TryAwaitFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time),
                TryAwaitFailureTest::brokenDiagnostics);

        uncontrolled(Attempt.Operation.CONDITION, attempts(nullEvaluation).getLast());
        assertInstanceOf(AssertionError.class, diagnostics.failure().cause());
        assertThat(diagnostics.failure().suppressed()).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullEvaluationHasTheSamePriorityAcrossConditionFamiliesAndComposition(boolean interrupt) {
        CheckedFunction<String, ConditionResult> evaluation = actual -> {
            if (interrupt) currentThread().interrupt();
            return null;
        };
        var preserved = Conditions.condition("broken", evaluation);
        List<Supplier<AwaitResult<?, ?>>> paths = List.of(
                () -> tryAwait(() -> "actual").until(preserved),
                () -> tryAwait(() -> "actual").until(captured(preserved, preserved)),
                () -> tryAwait(() -> Optional.of("actual")).until(OptionalConditions.hasValue(preserved)),
                () -> tryAwait(() -> List.of("actual")).until(CollectionConditions.singleElement(preserved)),
                () -> tryAwait(() -> Map.of("key", "actual")).until(MapConditions.valueFor("key", preserved)));
        for (var path : paths) {
            try {
                var result = failed(path.get());
                assertEquals(interrupt ? INTERRUPTED : CONDITION_FAILED, result.failure().reason());
                assertInstanceOf(NullPointerException.class, result.failure().cause());
                assertEquals("condition returned null ConditionResult", result.failure().cause().getMessage());
                assertEquals(1, attempts(result).size());
                assertEquals(interrupt, currentThread().isInterrupted());
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void capturedNullEvaluationRetainsCauseAndProgressWhenInterrupted() {
        var result = failed(timedTryAwait(() -> "actual", config(1, 2, 0), time, time).until(captured(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return null;
                }),
                condition("second stage", actual -> satisfied()))));
        var observation = uncontrolled(Attempt.Operation.CONDITION, attempts(result).getFirst());
        assertTrue(result.conditions().getLast().attempts().isEmpty());

        assertEquals(INTERRUPTED, result.failure().reason());
        assertEquals(NullPointerException.class, observation.cause().getClass());
        assertEquals("condition returned null ConditionResult", observation.cause().getMessage());
        assertEquals("first stage", result.conditions().getFirst().condition().description());
    }

    @Test
    void capturedInterruptRetainsSequenceProgress() {
        var result = failed(timedTryAwait(() -> "actual", config(1, 2, 0), time, time).until(captured(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return satisfied();
                }),
                condition("second stage", actual -> satisfied()))));
        uncontrolled(Attempt.Operation.CONDITION, attempts(result).getFirst());
        assertTrue(result.conditions().getLast().attempts().isEmpty());

        assertEquals(INTERRUPTED, result.failure().reason());
        assertEquals("first stage", result.conditions().getFirst().condition().description());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void sourceWaitingAndDiagnosticErrorsAreCapturedUniformly() {
        var pollingTime = new FakeTime(0);
        var sourceFatal = new InternalError("fatal source");
        var parkerFatal = new InternalError("fatal parker");
        var diagnosticsFatal = new InternalError("fatal diagnostics");

        var source = failed(tryAwait((Callable<Object>) () -> { throw sourceFatal; })
                .usingTime(pollingTime, pollingTime).until(isNotNull));
        assertSame(sourceFatal, source.failure().cause());
        assertEquals(SOURCE_FAILED, source.failure().reason());
        var parking = failed(timedTryAwait(Object::new, config(1, 2, 0), time,
                nanos -> { throw parkerFatal; }).until(condition("never", actual -> unsatisfied("not ready"))));
        assertSame(parkerFatal, parking.failure().cause());
        assertEquals(WAIT_FAILED, parking.failure().reason());
        var diagnosticTime = new FakeTime(0);
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> { throw diagnosticsFatal; });
        var diagnostics = failed(timedTryAwait(() -> actual, config(1, 2, 0),
                diagnosticTime, diagnosticTime).until(brokenDiagnostics()));
        assertSame(diagnosticsFatal, diagnostics.failure().suppressed().getFirst());
    }

    private static <VALUE> AwaitResult.Failed<?, ?> assertParity(
            Function<FakeTime, ValueAwait<VALUE>> waiting,
            Supplier<PreservingCondition<? super VALUE>> condition) {
        Throwable thrown;
        try {
            thrown = assertThrows(Throwable.class,
                    () -> waiting.apply(new FakeTime(0)).until(condition.get()));
        } finally {
            interrupted();
        }

        try {
            var captured = failed(diagnostic(waiting.apply(new FakeTime(0))).until(condition.get()));
            assertSameFailure(thrown, captured.failure());
            return captured;
        } finally {
            interrupted();
        }
    }

    private static void assertSameFailure(Throwable thrown, AwaitFailure captured) {
        AwaitFailure attached = switch (thrown) {
            case AwaitAssertionError assertion -> assertion.failure();
            case AwaitExecutionException execution -> execution.failure();
            default -> throw new AssertionError("unexpected failure", thrown);
        };
        assertEquals(attached.reason(), captured.reason());
        assertEquals(thrown.getMessage(), captured.message());
        if (thrown.getCause() == null) {
            assertNull(captured.cause());
        } else {
            assertThat(captured.cause()).isExactlyInstanceOf(thrown.getCause().getClass())
                    .hasMessage(thrown.getCause().getMessage());
        }
        assertThat(captured.suppressed()).zipSatisfy(List.of(thrown.getSuppressed()), (actual, expected) ->
                assertThat(actual).isExactlyInstanceOf(expected.getClass()).hasMessage(expected.getMessage()));
    }

    private static AwaitResult.Failed<?, ?> failed(AwaitResult<?, ?> result) {
        return assertInstanceOf(AwaitResult.Failed.class, result);
    }

    private static PreservingCondition<Object> unstableCondition() {
        int[] calls = {0};
        return condition("value remains ready", actual -> calls[0]++ == 0
                ? satisfied() : unsatisfied("not ready"));
    }

    private static Condition.PreservingCondition<Object> unstableAssertion() {
        int[] calls = {0};
        return asserted(actual -> {
            if (calls[0]++ > 0) {
                throw new AssertionError("persistence assertion failed");
            }
        });
    }

    private static Object brokenDiagnosticActual() {
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> { throw new IllegalStateException("description failed"); });
        return actual;
    }

    private static PreservingCondition<Object> brokenDiagnostics() {
        return condition("condition", actual ->
                unsatisfied("not ready", new AssertionError("engine")));
    }
}
