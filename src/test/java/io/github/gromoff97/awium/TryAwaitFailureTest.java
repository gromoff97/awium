package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Conditions.asserted;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TryAwaitFailureTest {

    private final FakeTime time = new FakeTime(0);

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @MethodSource("evaluationInterruptions")
    void interruptionAfterEvaluationRetainsTheOriginalCauseAndNestedLocation(
            Runnable onEvaluation, CheckedFunction<Throwable, ConditionResult<Throwable>> evaluation, String location) {
        Runnable[] clockEffect = { () -> {} };
        var cause = new IllegalStateException("not ready");
        var check = hasValue(condition("ready", (String actual) -> {
            clockEffect[0] = () -> currentThread().interrupt();
            onEvaluation.run();
            return evaluation.apply(cause);
        }));
        var result = tryAwait(() -> Optional.of("actual")).usingTime(() -> {
            clockEffect[0].run();
            return 0;
        }, nanos -> {}).until(check);
        var failure = failure(result);
        var attempt = uncontrolled(Operation.CONDITION, attempts(result).getFirst());
        assertEquals(INTERRUPTED, failure.reason());
        assertSame(cause, failure.cause());
        assertSame(cause, attempt.cause());
        assertEquals(location, attempt.location());
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
    void predicateTimeoutHasTheSameOutcomeInBothTerminals() {
        var timeout = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> false));
        assertInstanceOf(UnsatisfiedObservation.class, attempts(timeout).getLast());
    }

    @Test
    void assertionTimeoutHasTheSameOutcomeInBothTerminals() {
        var timeout = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> asserted(actual -> { throw new AssertionError("assertion failed"); }));
        assertInstanceOf(UnsatisfiedObservation.class, attempts(timeout).getLast());
    }

    @Test
    void unsatisfiedPersistenceHasTheSameOutcomeInBothTerminals() {
        var persistence = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryAwaitFailureTest::unstableCondition);
        assertInstanceOf(UnsatisfiedObservation.class, attempts(persistence).getLast());
    }

    @Test
    void assertionPersistenceHasTheSameOutcomeInBothTerminals() {
        var persistence = assertParity(time -> timedAwait(() -> "actual", config(1, 3, 2), time, time),
                TryAwaitFailureTest::unstableAssertion);
        assertInstanceOf(UnsatisfiedObservation.class, attempts(persistence).getLast());
    }

    @ParameterizedTest
    @MethodSource("sourceFailures")
    void capturesSourceFailuresWithoutChangingThem(Callable<Object> source) {
        var result = assertParity(time -> timedAwait(source, config(1, 2, 0), time, time), () -> isNotNull);
        uncontrolled(Operation.SOURCE, attempts(result).getLast());
    }

    @ParameterizedTest
    @MethodSource("conditionFailures")
    void capturesConditionFailuresWithoutChangingThem(Supplier<Throwable> failure) {
        var condition = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> {
                    throw failure.get();
                }));
        uncontrolled(Operation.CONDITION, attempts(condition).getLast());
    }

    @Test
    void capturesConditionFactoryFailuresInsideTheFirstAttempt() {
        var failure = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> {
                    throw new IllegalStateException("condition factory failed");
                }));

        assertEquals(1, attempts(failure).size());
        uncontrolled(Operation.CONDITION, attempts(failure).getFirst());
    }

    @Test
    void rejectsNullConditionEvaluators() {
        var failure = assertTypedParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> null));
        assertEquals(1, attempts(failure).size());
        uncontrolled(Operation.CONDITION, attempts(failure).getFirst());
        assertEquals("evaluator must not be null", failure.failure().cause().getMessage());
    }

    @ParameterizedTest
    @MethodSource("parkingConditions")
    void capturesParkingFailuresInBothPhases(long persistence, PreservingCondition<Object> check) {
        var parking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, persistence), time,
                        nanos -> { throw new IllegalStateException("park failed"); }),
                () -> check);

        assertEquals(2, attempts(parking).size());
        uncontrolled(Operation.WAITING, attempts(parking).getLast());
    }

    @Test
    void capturesInterruptionBeforeCallingTheSource() {
        var beforeSource = assertParity(time -> {
                    currentThread().interrupt();
                    return timedAwait(() -> "actual", config(1, 2, 0), time, time);
                }, () -> isNotNull);
        assertThat(beforeSource.failure().message()).startsWith("Waiting interrupted");
    }

    @Test
    void capturesAThrownInterruptionAsASourceFailure() {
        var thrownSourceInterruption = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertEquals(SOURCE_FAILED, thrownSourceInterruption.failure().reason());
        assertThat(thrownSourceInterruption.failure().message()).startsWith("Source retrieval failed");
    }

    @Test
    void capturesSourceInterruptionWithItsObservationAndElapsedTime() {
        var sourceFlag = assertParity(time -> timedAwait((Callable<String>) () -> {
                    time.advanceNanos(7);
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertThat(sourceFlag.failure().message()).startsWith("Source retrieval interrupted");
        assertThat(sourceFlag.failure().message()).contains("elapsed 7 ns", "Actual: \"actual\"");
    }

    @Test
    void capturesPredicateInterruption() {
        var condition = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> {
                    currentThread().interrupt();
                    return true;
                }));

        uncontrolled(Operation.CONDITION, attempts(condition).getLast());
        assertThat(condition.failure().message()).startsWith("Condition evaluation interrupted");
    }

    @Test
    void diagnosticConditionInterruptionPreservesTheThreadFlag() {
        failed(timedTryAwait(() -> "actual", config(1, 2, 0),
                time, time).until(condition("condition succeeds", actual -> {
                    currentThread().interrupt();
                    return satisfied(actual);
                })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluation() {
        var nullEvaluation = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> null));

        uncontrolled(Operation.CONDITION, attempts(nullEvaluation).getLast());
    }

    @Test
    void capturesDiagnosticRenderingFailure() {
        var diagnostics = assertTypedParity(time -> timedAwait(
                        TryAwaitFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time),
                TryAwaitFailureTest::brokenDiagnostics);

        assertInstanceOf(AssertionError.class, diagnostics.failure().cause());
        assertThat(diagnostics.failure().suppressed()).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @MethodSource("nullEvaluationSources")
    void nullEvaluationIsAConditionFailure(Function<Condition.Typed<String, String>, AwaitResult<?, ?>> source) {
        var result = failed(source.apply(condition("broken", actual -> null)));
        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertInstanceOf(NullPointerException.class, result.failure().cause());
        assertEquals("condition returned null ConditionResult", result.failure().cause().getMessage());
        assertEquals(1, attempts(result).size());
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("nullEvaluationSources")
    void interruptionTakesPriorityOverNullEvaluation(Function<Condition.Typed<String, String>, AwaitResult<?, ?>> source) {
        var result = failed(source.apply(condition("broken", actual -> {
            currentThread().interrupt();
            return null;
        })));
        assertEquals(INTERRUPTED, result.failure().reason());
        assertInstanceOf(NullPointerException.class, result.failure().cause());
        assertEquals("condition returned null ConditionResult", result.failure().cause().getMessage());
        assertEquals(1, attempts(result).size());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("interruptedEvaluations")
    void capturedInterruptRetainsCauseAndSequenceProgress(ConditionResult<String> evaluation, Class<? extends Throwable> causeType, String message) {
        var result = failed(timedTryAwait(() -> "actual", config(1, 2, 0), time, time).until(captured(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return evaluation;
                }),
                condition("second stage", actual -> satisfied(actual)))));
        var observation = uncontrolled(Operation.CONDITION, attempts(result).getFirst());
        assertTrue(result.conditions().getLast().attempts().isEmpty());

        assertEquals(INTERRUPTED, result.failure().reason());
        assertThat(observation.cause()).isExactlyInstanceOf(causeType).hasMessage(message);
        assertEquals("first stage", result.conditions().getFirst().condition().description());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesFatalSourceErrors() {
        var sourceFatal = new InternalError("fatal source");
        var source = failed(tryAwait((Callable<Object>) () -> { throw sourceFatal; })
                .usingTime(time, time).until(isNotNull));
        assertSame(sourceFatal, source.failure().cause());
        assertEquals(SOURCE_FAILED, source.failure().reason());
    }

    @Test
    void capturesFatalParkingErrors() {
        var parkerFatal = new InternalError("fatal parker");
        var parking = failed(timedTryAwait(Object::new, config(1, 2, 0), time,
                nanos -> { throw parkerFatal; }).until(condition("never", actual -> unsatisfied("not ready"))));
        assertSame(parkerFatal, parking.failure().cause());
        assertEquals(WAIT_FAILED, parking.failure().reason());
    }

    @Test
    void capturesFatalDiagnosticErrors() {
        var diagnosticsFatal = new InternalError("fatal diagnostics");
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> { throw diagnosticsFatal; });
        var diagnostics = failed(timedTryAwait(() -> actual, config(1, 2, 0),
                time, time).until(brokenDiagnostics()));
        assertSame(diagnosticsFatal, diagnostics.failure().suppressed().getFirst());
    }

    private static <VALUE> Failed<?, ?> assertParity(Function<FakeTime, ValueAwait<VALUE>> waiting, Supplier<PreservingCondition<? super VALUE>> condition) {
        return assertParity(() -> waiting.apply(new FakeTime(0)).until(condition.get()),
                () -> diagnostic(waiting.apply(new FakeTime(0))).until(condition.get()));
    }

    private static <VALUE> Failed<?, ?> assertTypedParity(
            Function<FakeTime, ValueAwait<VALUE>> waiting, Supplier<Condition.Typed<? super VALUE, ?>> condition) {
        return assertParity(() -> waiting.apply(new FakeTime(0)).until(condition.get()),
                () -> diagnostic(waiting.apply(new FakeTime(0))).until(condition.get()));
    }

    private static Failed<?, ?> assertParity(Executable waiting, Supplier<AwaitResult<?, ?>> diagnostic) {
        var thrown = assertThrows(Throwable.class, waiting);
        interrupted();
        var captured = failed(diagnostic.get());
        assertSameFailure(thrown, captured.failure());
        interrupted();
        return captured;
    }

    private static void assertSameFailure(Throwable thrown, AwaitFailure captured) {
        AwaitFailure attached = switch (thrown) {
            case AwaitAssertionError assertion -> assertion.failure();
            case AwaitExecutionException execution -> execution.failure();
            default -> throw new AssertionError("unexpected failure", thrown);
        };
        assertEquals(attached.reason(), captured.reason());
        assertEquals(thrown.getMessage(), captured.message());
        assertEquals(Optional.ofNullable(thrown.getCause()).map(Object::getClass),
                Optional.ofNullable(captured.cause()).map(Object::getClass));
        assertEquals(Optional.ofNullable(thrown.getCause()).map(Throwable::getMessage),
                Optional.ofNullable(captured.cause()).map(Throwable::getMessage));
        assertThat(captured.suppressed()).zipSatisfy(List.of(thrown.getSuppressed()), (actual, expected) ->
                assertThat(actual).isExactlyInstanceOf(expected.getClass()).hasMessage(expected.getMessage()));
    }

    private static Failed<?, ?> failed(AwaitResult<?, ?> result) {
        return assertInstanceOf(Failed.class, result);
    }

    private static List<Named<Callable<Object>>> sourceFailures() {
        return List.of(named("exception", () -> { throw new IllegalStateException("source failed"); }),
                named("assertion", () -> { throw new AssertionError("source assertion"); }));
    }

    private static List<Named<Function<Condition.Typed<String, String>, AwaitResult<?, ?>>>> nullEvaluationSources() {
        return List.of(named("value", condition -> tryAwait(() -> "actual").until(condition)),
                named("captured", condition -> tryAwait(() -> "actual").until(captured(condition, condition))),
                named("optional", condition -> tryAwait(() -> Optional.of("actual")).until(hasValue(condition))),
                named("collection", condition -> tryAwait(() -> List.of("actual")).until(singleElement(condition))),
                named("map", condition -> tryAwait(() -> Map.of("key", "actual")).until(valueFor("key", condition))));
    }

    private static List<Arguments> evaluationInterruptions() {
        Runnable interrupt = () -> currentThread().interrupt();
        Runnable continueEvaluation = () -> {};
        CheckedFunction<Throwable, ConditionResult<Throwable>> mismatch = cause -> unsatisfied("not ready", cause);
        CheckedFunction<Throwable, ConditionResult<Throwable>> throwing = cause -> { throw cause; };
        return List.of(argumentSet("callback mismatch", interrupt, mismatch, "optional value"),
                argumentSet("callback exception", interrupt, throwing, "optional value"),
                argumentSet("clock mismatch", continueEvaluation, mismatch, null),
                argumentSet("clock exception", continueEvaluation, throwing, "optional value"));
    }

    private static List<Named<Supplier<Throwable>>> conditionFailures() {
        return List.of(named("exception", () -> new IllegalStateException("condition failed")),
                named("assertion", () -> new AssertionError("condition assertion")));
    }

    private static List<Arguments> interruptedEvaluations() {
        return List.of(argumentSet("null result", null, NullPointerException.class, "condition returned null ConditionResult"),
                argumentSet("satisfied result", satisfied("actual"), InterruptedException.class, "caller thread interrupt flag was set"));
    }

    private static List<Arguments> parkingConditions() {
        return List.of(argumentSet("acquisition", 0L, matches(actual -> false)),
                argumentSet("persistence", 2L, isNotNull));
    }

    private static Condition.Typed<Object, Object> unstableCondition() {
        var evaluations = List.<ConditionResult<Object>>of(satisfied("actual"), unsatisfied("not ready")).iterator();
        return condition("value remains ready", actual -> evaluations.next());
    }

    private static PreservingCondition<Object> unstableAssertion() {
        List<Runnable> evaluations = List.of(() -> {}, () -> { throw new AssertionError("persistence assertion failed"); });
        var next = evaluations.iterator();
        return asserted(actual -> next.next().run());
    }

    private static Object brokenDiagnosticActual() {
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> { throw new IllegalStateException("description failed"); });
        return actual;
    }

    private static Condition.Typed<Object, Object> brokenDiagnostics() {
        return condition("condition", actual ->
                unsatisfied("not ready", new AssertionError("engine")));
    }
}
