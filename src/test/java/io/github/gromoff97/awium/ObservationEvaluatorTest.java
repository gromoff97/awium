package io.github.gromoff97.awium;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.sources.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class ObservationEvaluatorTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void recordsEveryCompletedStageRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        WaitOutcome<Object, Object> terminal = new WaitEngine(config(1, 20, 0), time, time).waitFor(() -> {
            time.advanceNanos(2);
            return actual;
        }, value -> {
            time.advanceNanos(3);
            return satisfied(result);
        });

        AwaitAttempt<Object, Object> attempt = terminal.attempt();
        var outcome = assertInstanceOf(AwaitAttempt.Outcome.Satisfied.class, attempt.outcome());
        var timing = outcome.timing();
        assertEquals(1, attempt.number());
        assertEquals(ACQUISITION, attempt.phase());
        assertEquals(Duration.ZERO, timing.startOffset());
        assertEquals(Duration.ZERO, timing.retrievalOffset());
        assertEquals(Duration.ofNanos(2), timing.observationOffset());
        assertEquals(Duration.ofNanos(5), timing.completionOffset());
        assertSame(actual, outcome.observed());
        assertSame(result, outcome.result());
    }

    @Test
    void retainsOptionalAssertionCauseOnUnsatisfiedAttempts() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        var ordinary = attempt(() -> actual, value -> Evaluation.unsatisfied("not ready"));
        var asserted = attempt(() -> actual, value -> assertionUnsatisfied("assertion did not pass", assertion));

        var ordinaryOutcome = assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class, ordinary.outcome());
        var assertedOutcome = assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class, asserted.outcome());
        assertSame(actual, ordinaryOutcome.observed());
        assertEquals("not ready", ordinaryOutcome.mismatch());
        assertNull(ordinaryOutcome.assertion());
        assertSame(actual, assertedOutcome.observed());
        assertEquals("assertion did not pass", assertedOutcome.mismatch());
        assertSame(assertion, assertedOutcome.assertion());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableBeforeObservation(Throwable failure) {
        var attempt = attempt(() -> throwFailure(failure), ObservationEvaluatorTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertEquals(Duration.ZERO, outcome.timing().startOffset());
        assertEquals(Duration.ZERO, outcome.timing().retrievalOffset());
        assertEquals(Duration.ZERO, outcome.timing().completionOffset());
    }

    @ParameterizedTest
    @MethodSource("uncheckedNonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAfterObservation(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> throwUnchecked(failure));

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertSame(actual, outcome.observed());
    }

    @Test
    void classifiesAnEvaluationOwnedUncontrolledCauseAsConditionFailure() {
        var actual = new Object();
        var failure = new IllegalStateException("built-in failed");
        var attempt = attempt(() -> actual, value -> uncontrolled(failure));

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertSame(actual, outcome.observed());
    }

    @Test
    void evaluationOwnedInterruptionIsPreservedAndRestored() {
        var actual = new Object();
        var interruption = new InterruptedException("condition stopped");
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return uncontrolled(interruption);
        });

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(interruption, outcome.failure());
        assertSame(actual, outcome.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var attempt = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(actual, outcome.observed());
        assertEquals(NullPointerException.class, outcome.failure().getClass());
        assertEquals("condition returned null Evaluation", outcome.failure().getMessage());
        assertEquals(1, conditionCalls[0]);
    }

    @Test
    void thrownSourceInterruptionIsPreservedAndRestored() {
        var interruption = new InterruptedException("source stopped");
        var attempt = attempt(() -> {
            throw interruption;
        }, ObservationEvaluatorTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(interruption, outcome.failure());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void thrownConditionInterruptionIsPreservedWithActualAndRestored() {
        var actual = new Object();
        var interruption = new InterruptedException("condition stopped");
        var attempt = attempt(() -> actual, value -> throwUnchecked(interruption));

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(interruption, outcome.failure());
        assertSame(actual, outcome.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void sourceFlagAfterNormalReturnRetainsObservationAndSkipsCondition() {
        var actual = new Object();
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return actual;
        }, ObservationEvaluatorTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceInterrupted.class, attempt.outcome());
        assertSame(actual, outcome.observed());
        assertEquals("caller thread interrupt flag was set", outcome.failure().getMessage());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied(new Object());
        });

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(actual, outcome.observed());
        assertEquals(InterruptedException.class, outcome.failure().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return throwFailure(failure);
        }, ObservationEvaluatorTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("uncheckedNonFatalThrowables")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return throwUnchecked(failure);
        });

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertSame(actual, outcome.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromSourceEscapeRawAndSkipCondition(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(() -> throwFailure(fatal), ObservationEvaluatorTest::failIfCalled)));
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromConditionEscapeRaw(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(Object::new, value -> throwUnchecked(fatal))));
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void evaluationOwnedFatalSignalsEscapeRaw(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(), () -> attempt(Object::new, value -> {
            currentThread().interrupt();
            return uncontrolled(fatal);
        })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void abruptFatalThrowableWinsOverCallbackSetFlag() {
        var sourceFatal = new InternalError("source fatal");
        assertSame(sourceFatal, assertThrows(InternalError.class, () -> attempt(() -> {
            currentThread().interrupt();
            throw sourceFatal;
        }, ObservationEvaluatorTest::failIfCalled)));
        assertTrue(currentThread().isInterrupted());
        interrupted();

        var conditionFatal = new InternalError("condition fatal");
        assertSame(conditionFatal, assertThrows(InternalError.class, () -> attempt(Object::new, actual -> {
            currentThread().interrupt();
            throw conditionFatal;
        })));
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Throwable> nonFatalThrowables() {
        return Stream.of(new Exception("checked"), new AssertionError("assertion"));
    }

    private static Stream<Throwable> uncheckedNonFatalThrowables() {
        return Stream.of(new RuntimeException("runtime"), new AssertionError("assertion"));
    }

    private static Stream<Error> fatalSignals() {
        return Stream.of(new InternalError("fatal"), new ThreadDeath());
    }

    private static <T> T throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw (Error) failure;
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }

    private static <T, R> Evaluation<R> failIfCalled(T ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static <R> AwaitAttempt<Object, R> attempt(Source<Object> source,
            Function<Object, Evaluation<R>> condition) {
        var time = new FakeTime(0);
        return new WaitEngine(config(1, 2, 0), time, time).waitFor(source, actual -> {
            Evaluation<R> evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        }).attempt();
    }

    private static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }
}
