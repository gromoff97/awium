package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.AwaitAssertions.uncontrolled;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static io.github.gromoff97.awium.support.TestConditions.matching;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void unsatisfiedPersistenceHasTheSameOutcomeInBothTerminals() {
        var persistence = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryAwaitFailureTest::unstableCondition);
        assertInstanceOf(UnsatisfiedObservation.class, attempts(persistence).getLast());
    }

    @ParameterizedTest
    @MethodSource("sourceFailures")
    void capturesSourceFailuresWithoutChangingThem(Callable<Object> source) {
        var result = assertParity(time -> timedAwait(source, config(1, 2, 0), time, time), () -> accepted);
        uncontrolled(Operation.SOURCE, attempts(result).getLast());
    }

    @ParameterizedTest
    @MethodSource("conditionFailures")
    void capturesConditionFailuresWithoutChangingThem(Supplier<Throwable> failure) {
        var condition = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> check("condition succeeds", actual -> {
                    throw failure.get();
                }));
        uncontrolled(Operation.CONDITION, attempts(condition).getLast());
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
                }, () -> accepted);
        assertThat(beforeSource.failure().message()).startsWith("Waiting interrupted");
    }

    @Test
    void capturesAThrownInterruptionAsASourceFailure() {
        var thrownSourceInterruption = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time),
                () -> accepted);
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
                () -> accepted);
        assertThat(sourceFlag.failure().message()).startsWith("Source retrieval interrupted");
        assertThat(sourceFlag.failure().message()).contains("elapsed 7 ns", "Actual: \"actual\"");
    }

    @Test
    void diagnosticConditionInterruptionPreservesTheThreadFlag() {
        failed(timedTryAwait(() -> "actual", config(1, 2, 0),
                time, time).until(check("condition succeeds", actual -> {
                    currentThread().interrupt();
                    return satisfied(actual);
                })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluation() {
        var nullEvaluation = assertTypedParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> check("condition succeeds", actual -> null));

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

    @Test
    void capturesFatalSourceErrors() {
        var sourceFatal = new InternalError("fatal source");
        var source = failed(withTime(tryAwait((Callable<Object>) () -> { throw sourceFatal; }), time, time).until(accepted));
        assertSame(sourceFatal, source.failure().cause());
        assertEquals(SOURCE_FAILED, source.failure().reason());
    }

    @Test
    void capturesFatalParkingErrors() {
        var parkerFatal = new InternalError("fatal parker");
        var parking = failed(timedTryAwait(Object::new, config(1, 2, 0), time,
                nanos -> { throw parkerFatal; }).until(check("never", actual -> unsatisfied("not ready"))));
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
            Function<FakeTime, ValueAwait<VALUE>> waiting, Supplier<Condition<? super VALUE, ?>> condition) {
        return assertParity(() -> waiting.apply(new FakeTime(0)).until(condition.get()),
                () -> diagnostic(waiting.apply(new FakeTime(0))).until(condition.get()));
    }

    private static Failed<?, ?> assertParity(Executable waiting, Supplier<AwaitResult<?, ?>> diagnostic) {
        var thrown = assertThrows(Throwable.class, waiting);
        interrupted();
        var returned = failed(diagnostic.get());
        assertSameFailure(thrown, returned.failure());
        interrupted();
        return returned;
    }

    private static void assertSameFailure(Throwable thrown, AwaitFailure returned) {
        AwaitFailure attached = switch (thrown) {
            case AwaitAssertionError assertion -> assertion.failure();
            case AwaitExecutionException execution -> execution.failure();
            default -> throw new AssertionError("unexpected failure", thrown);
        };
        assertEquals(attached.reason(), returned.reason());
        assertEquals(thrown.getMessage(), returned.message());
        assertEquals(Optional.ofNullable(thrown.getCause()).map(Object::getClass),
                Optional.ofNullable(returned.cause()).map(Object::getClass));
        assertEquals(Optional.ofNullable(thrown.getCause()).map(Throwable::getMessage),
                Optional.ofNullable(returned.cause()).map(Throwable::getMessage));
        assertThat(returned.suppressed()).zipSatisfy(List.of(thrown.getSuppressed()), (actual, expected) ->
                assertThat(actual).isExactlyInstanceOf(expected.getClass()).hasMessage(expected.getMessage()));
    }

    private static Failed<?, ?> failed(AwaitResult<?, ?> result) {
        return assertInstanceOf(Failed.class, result);
    }

    private static List<Named<Callable<Object>>> sourceFailures() {
        return List.of(named("exception", () -> { throw new IllegalStateException("source failed"); }),
                named("assertion", () -> { throw new AssertionError("source assertion"); }));
    }

    private static List<Named<Supplier<Throwable>>> conditionFailures() {
        return List.of(named("exception", () -> new IllegalStateException("condition failed")),
                named("assertion", () -> new AssertionError("condition assertion")));
    }

    private static List<Arguments> parkingConditions() {
        return List.of(argumentSet("acquisition", 0L, matching(actual -> false)),
                argumentSet("persistence", 2L, accepted));
    }

    private static Condition<Object, Object> unstableCondition() {
        var evaluations = List.<ConditionResult<Object>>of(satisfied("actual"), unsatisfied("not ready")).iterator();
        return check("value remains ready", actual -> evaluations.next());
    }

    private static Object brokenDiagnosticActual() {
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> { throw new IllegalStateException("description failed"); });
        return actual;
    }

    private static Condition<Object, Object> brokenDiagnostics() {
        return check("condition", actual ->
                unsatisfied("not ready", new AssertionError("engine")));
    }
}
