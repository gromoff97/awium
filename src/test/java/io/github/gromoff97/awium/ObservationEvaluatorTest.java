package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.Attempt.Origin.*;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("removal")
class ObservationEvaluatorTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void classifiesSatisfiedEvaluationsAndRetainsTheCurrentActual() {
        var actual = new Object();
        var result = new Object();
        var calls = new int[2];

        Attempt<Object> outcome = attempt(() -> {
            calls[0]++;
            return actual;
        }, value -> {
            calls[1]++;
            assertSame(actual, value);
            return satisfied(result);
        });

        var satisfied = assertInstanceOf(Satisfied.class, outcome);
        assertSame(actual, satisfied.actual());
        assertSame(result, satisfied.result());
        assertEquals(1, satisfied.number());
        assertEquals(2, satisfied.completedNanos());
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void classifiesUnsatisfiedEvaluationsAndPreservesAssertionContext() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        Attempt<Object> outcome = attempt(() -> actual,
                value -> assertionUnsatisfied(
                        "assertion did not pass", assertion));

        var unsatisfied = assertInstanceOf(Unsatisfied.class, outcome);
        assertSame(actual, unsatisfied.actual());
        assertEquals("assertion did not pass", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
        assertEquals(1, unsatisfied.number());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableAtItsOrigin(Throwable failure) {
        Attempt<Object> outcome = attempt(
                () -> throwFailure(failure),
                ObservationEvaluatorTest::failIfCalled);

        var uncontrolled = assertInstanceOf(
                BeforeObservation.class, outcome);
        assertEquals(SOURCE, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertEquals(1, uncontrolled.number());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAndRetainsActual(
            Throwable failure) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(
                () -> actual, value -> throwFailure(failure));

        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(CONDITION, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertSame(actual, uncontrolled.actual());
        assertEquals(1, uncontrolled.number());
    }

    @Test
    void classifiesAnEvaluationOwnedUncontrolledCauseAsConditionOrigin() {
        var actual = new Object();
        var failure = new IllegalStateException("built-in failed");

        Attempt<Object> outcome = attempt(() -> actual,
                value -> uncontrolled(failure));

        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(CONDITION, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertSame(actual, uncontrolled.actual());
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(CONDITION, uncontrolled.origin());
        assertSame(actual, uncontrolled.actual());
        assertEquals(NullPointerException.class,
                uncontrolled.cause().getClass());
        assertEquals("condition returned null Evaluation",
                uncontrolled.cause().getMessage());
        assertEquals(1, conditionCalls[0]);
    }

    @Test
    void thrownSourceInterruptionIsPreservedAndRestored() {
        var interrupted = new InterruptedException("source stopped");

        Attempt<Object> outcome = attempt(() -> {
            throw interrupted;
        }, ObservationEvaluatorTest::failIfCalled);

        var uncontrolled = assertInstanceOf(
                BeforeObservation.class, outcome);
        assertEquals(SOURCE, uncontrolled.origin());
        assertSame(interrupted, uncontrolled.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void thrownConditionInterruptionIsPreservedWithActualAndRestored() {
        var actual = new Object();
        var interrupted = new InterruptedException("condition stopped");

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            throw interrupted;
        });

        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(CONDITION, uncontrolled.origin());
        assertSame(interrupted, uncontrolled.cause());
        assertSame(actual, uncontrolled.actual());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromSourceEscapeRawAndSkipCondition(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(() -> throwFailure(fatal),
                        ObservationEvaluatorTest::failIfCalled)));
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromConditionEscapeRaw(Error fatal) {
        var actual = new Object();

        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(() -> actual,
                        value -> throwFailure(fatal))));
    }

    @Test
    void sourceFlagAfterNormalReturnWinsAndSkipsCondition() {
        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            return null;
        }, ObservationEvaluatorTest::failIfCalled);

        assertFlagInterruption(outcome, SOURCE, null);
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied(new Object());
        });

        assertFlagInterruption(outcome, CONDITION, actual);
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            return throwFailure(failure);
        }, ObservationEvaluatorTest::failIfCalled);

        var uncontrolled = assertInstanceOf(
                BeforeObservation.class, outcome);
        assertEquals(SOURCE, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return throwFailure(failure);
        });

        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(CONDITION, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertSame(actual, uncontrolled.actual());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void abruptFatalThrowableWinsOverCallbackSetFlag() {
        var sourceFatal = new InternalError("source fatal");
        assertSame(sourceFatal, assertThrows(InternalError.class,
                () -> attempt(() -> {
                    currentThread().interrupt();
                    throw sourceFatal;
                }, ObservationEvaluatorTest::failIfCalled)));
        assertTrue(currentThread().isInterrupted());
        interrupted();

        var conditionFatal = new InternalError("condition fatal");
        assertSame(conditionFatal, assertThrows(InternalError.class,
                () -> attempt(Object::new, actual -> {
                    currentThread().interrupt();
                    throw conditionFatal;
                })));
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Throwable> nonFatalThrowables() {
        return Stream.of(
                new Exception("checked"),
                new AssertionError("assertion"));
    }

    private static Stream<Error> fatalSignals() {
        return Stream.of(
                new InternalError("fatal"),
                new ThreadDeath());
    }

    private static <T> T throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw (Error) failure;
    }

    private static <T, R> Evaluation<R> failIfCalled(T ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static <R> Attempt<R> attempt(Source<Object> source,
            CheckedFunction<Object, Evaluation<R>> condition) {
        var time = new FakeTime(0);
        return new WaitEngine(new WaitConfiguration(1, 2, 0), time, time)
                .waitFor(source, new RuntimeCondition<>(actual -> {
                    Evaluation<R> evaluation = condition.apply(actual);
                    time.advanceNanos(2);
                    return evaluation;
                }, () -> "test condition", null)).attempt();
    }

    private static void assertFlagInterruption(Attempt<?> outcome,
            Origin origin, Object actual) {
        var uncontrolled = assertInstanceOf(
                AfterObservation.class, outcome);
        assertEquals(origin, uncontrolled.origin());
        assertSame(actual, uncontrolled.actual());
        assertEquals(1, uncontrolled.number());
        assertEquals(InterruptedException.class,
                uncontrolled.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                uncontrolled.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }
}
