package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("removal")
class ObservationEvaluatorTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void attemptVariantsExposeOnlyApplicableState() {
        var satisfied = new Attempt.Satisfied<>(
                null, null, 1, Long.MIN_VALUE);
        var unsatisfied = new Attempt.Unsatisfied<>(
                null, "not ready", null, 2, 20);
        var before = new Attempt.Uncontrolled.BeforeObservation<String>(
                Attempt.Origin.SOURCE, new RuntimeException("source"), 3, 30);
        var after = new Attempt.Uncontrolled.AfterObservation<String>(
                Attempt.Origin.CONDITION, null,
                new RuntimeException("condition"), 4, 40);

        assertAll(
                () -> assertNull(satisfied.actual()),
                () -> assertNull(satisfied.result()),
                () -> assertEquals(Long.MIN_VALUE,
                        satisfied.completedNanos()),
                () -> assertNull(unsatisfied.actual()),
                () -> assertEquals("not ready", unsatisfied.mismatch()),
                () -> assertEquals(Attempt.Origin.SOURCE, before.origin()),
                () -> assertEquals(Attempt.Origin.CONDITION, after.origin()),
                () -> assertNull(after.actual()),
                () -> assertEquals(4, after.number()));
    }

    @Test
    void attemptVariantsValidateTheirOwnRequiredFields() {
        RuntimeException cause = new RuntimeException();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt.Satisfied<>("actual", "result", 0, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt.Unsatisfied<>(
                                "actual", null, null, 1, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt.Uncontrolled.BeforeObservation<>(
                                null, cause, 1, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt.Uncontrolled.AfterObservation<>(
                                Attempt.Origin.CONDITION, "actual", null, 1, 1)));
    }

    @ParameterizedTest
    @MethodSource("actualValues")
    void classifiesSatisfiedEvaluationsAndRetainsTheCurrentActual(Object actual) {
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

        var satisfied = assertInstanceOf(Attempt.Satisfied.class, outcome);
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

        var unsatisfied = assertInstanceOf(Attempt.Unsatisfied.class, outcome);
        assertSame(actual, unsatisfied.actual());
        assertEquals("assertion did not pass", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
        assertEquals(1, unsatisfied.number());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableAtItsOrigin(Throwable failure) {
        var conditionCalls = new int[1];

        Attempt<Object> outcome = attempt(
                () -> throwFailure(failure), value -> {
                    conditionCalls[0]++;
                    return satisfied(value);
                });

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class, outcome);
        assertEquals(Attempt.Origin.SOURCE, uncontrolled.origin());
        assertSame(failure, uncontrolled.cause());
        assertEquals(1, uncontrolled.number());
        assertEquals(0, conditionCalls[0]);
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAndRetainsActual(
            Throwable failure) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(
                () -> actual, value -> throwFailure(failure));

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertEquals(Attempt.Origin.CONDITION, uncontrolled.origin());
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
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertEquals(Attempt.Origin.CONDITION, uncontrolled.origin());
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
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertEquals(Attempt.Origin.CONDITION, uncontrolled.origin());
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
            currentThread().interrupt();
            throw interrupted;
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class, outcome);
        assertEquals(Attempt.Origin.SOURCE, uncontrolled.origin());
        assertSame(interrupted, uncontrolled.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void thrownConditionInterruptionIsPreservedWithActualAndRestored() {
        var actual = new Object();
        var interrupted = new InterruptedException("condition stopped");

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            currentThread().interrupt();
            throw interrupted;
        });

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertEquals(Attempt.Origin.CONDITION, uncontrolled.origin());
        assertSame(interrupted, uncontrolled.cause());
        assertSame(actual, uncontrolled.actual());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void fatalSignalsFromSourceEscapeRawAndSkipCondition() {
        var conditionCalls = new int[1];
        var virtualMachineError = new InternalError("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(
                InternalError.class,
                () -> attempt(() -> {
                    throw virtualMachineError;
                }, value -> {
                    conditionCalls[0]++;
                    return satisfied(value);
                })));
        assertSame(threadDeath, assertThrows(ThreadDeath.class,
                () -> attempt(() -> {
                    throw threadDeath;
                }, Evaluation::satisfied)));
        assertEquals(0, conditionCalls[0]);
    }

    @Test
    void fatalSignalsFromConditionEscapeRaw() {
        var actual = new Object();
        var virtualMachineError = new InternalError("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(
                InternalError.class,
                () -> attempt(() -> actual, value -> {
                    throw virtualMachineError;
                })));
        assertSame(threadDeath, assertThrows(ThreadDeath.class,
                () -> attempt(() -> actual, value -> {
                    throw threadDeath;
                })));
    }

    @ParameterizedTest
    @MethodSource("actualValues")
    void sourceFlagAfterNormalReturnWinsAndSkipsCondition(Object actual) {
        var conditionCalls = new int[1];

        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            return actual;
        }, value -> {
            conditionCalls[0]++;
            return satisfied(value);
        });

        assertFlagInterruption(outcome, Attempt.Origin.SOURCE, actual);
        assertEquals(0, conditionCalls[0]);
    }

    @ParameterizedTest
    @MethodSource("normalConditionReturns")
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation(
            Evaluation<Object> evaluation) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return evaluation;
        });

        assertFlagInterruption(outcome, Attempt.Origin.CONDITION, actual);
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            return throwFailure(failure);
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class, outcome);
        assertEquals(Attempt.Origin.SOURCE, uncontrolled.origin());
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
                Attempt.Uncontrolled.AfterObservation.class, outcome);
        assertEquals(Attempt.Origin.CONDITION, uncontrolled.origin());
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
                }, Evaluation::satisfied)));
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

    private static Stream<Arguments> actualValues() {
        return Stream.of(Arguments.of(new Object()), Arguments.of((Object) null));
    }

    private static Stream<Throwable> nonFatalThrowables() {
        return Stream.of(
                new Exception("checked"),
                new IllegalStateException("runtime"),
                new AssertionError("assertion"),
                new LinkageError("error"));
    }

    private static Stream<Arguments> normalConditionReturns() {
        return Stream.of(
                Arguments.of(satisfied(new Object())),
                Arguments.of(unsatisfied("not ready")),
                Arguments.of((Object) null));
    }

    private static <T> T throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
    }

    private static <R> Attempt<R> attempt(Source<Object> source,
            CheckedFunction<Object, Evaluation<R>> condition) {
        var time = new FakeTime(0);
        RuntimeCondition<Object, R> runtime = new RuntimeCondition<>(actual -> {
            Evaluation<R> evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        }, () -> "test condition", null);
        return new WaitEngine(new WaitConfiguration(1, 2, 0), time, time)
                .waitFor(source, runtime).attempt();
    }

    private static void assertFlagInterruption(Attempt<?> outcome,
            Attempt.Origin origin, Object actual) {
        var uncontrolled = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class, outcome);
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
