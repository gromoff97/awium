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
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void exposesOneValidatedAttemptRecord() {
        Attempt<String> attempt = satisfied(
                "actual", "result", 1, 123);

        assertEquals(Attempt.Status.SATISFIED, attempt.status());
        assertEquals(123, attempt.completedNanos());
    }

    @Test
    void rejectsMissingStatusAndNonPositiveAttemptNumbers() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt<>(null, null, true, "actual",
                                "result", null, null, null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> satisfied(
                                "actual", "result", 0, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> satisfied(
                                "actual", "result", -1, 1)));
    }

    @Test
    void rejectsInvalidSatisfiedFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.SATISFIED,
                                Attempt.Origin.SOURCE, true, "actual", "result",
                                null, null, null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.SATISFIED,
                                null, true, "actual", "result", null, null,
                                new RuntimeException(), 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.SATISFIED,
                                null, false, null, "result", null, null,
                                null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.SATISFIED,
                                null, true, "actual", "result", "mismatch",
                                null, null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.SATISFIED,
                                null, true, "actual", "result", null,
                                new AssertionError(), null, 1, 1)));
    }

    @Test
    void rejectsInvalidUnsatisfiedFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNSATISFIED,
                                Attempt.Origin.CONDITION, true, "actual", null,
                                "mismatch", null, null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNSATISFIED,
                                null, true, "actual", null, "mismatch", null,
                                new RuntimeException(), 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNSATISFIED,
                                null, false, null, null, "mismatch", null,
                                null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNSATISFIED,
                                null, true, "actual", "result", "mismatch",
                                null, null, 1, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt<>(Attempt.Status.UNSATISFIED,
                                null, true, "actual", null, null, null,
                                null, 1, 1)));
    }

    @Test
    void rejectsInvalidUncontrolledFields() {
        var cause = new RuntimeException();
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                null, false, null, null, null, null, cause,
                                1, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                Attempt.Origin.SOURCE, false, null, null, null,
                                null, null, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                Attempt.Origin.SOURCE, false, null, "result",
                                null, null, cause, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                Attempt.Origin.SOURCE, false, null, null,
                                "mismatch", null, cause, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                Attempt.Origin.SOURCE, false, null, null, null,
                                new AssertionError(), cause, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Attempt<>(Attempt.Status.UNCONTROLLED,
                                Attempt.Origin.SOURCE, false, "actual", null,
                                null, null, cause, 1, 1)));
    }

    @Test
    void acceptsValidNullPayloadsAndWrappedAttemptCompletionTime() {
        Attempt<Object> satisfied = satisfied(null, null, 1, 1);
        Attempt<Object> unsatisfied = unsatisfied(
                null, "mismatch", new AssertionError(), 1, 1);
        Attempt<Object> uncontrolledWithoutActual = uncontrolled(
                Attempt.Origin.SOURCE, false, null,
                new RuntimeException(), 1, 1);
        Attempt<Object> uncontrolledWithNullActual = uncontrolled(
                Attempt.Origin.CONDITION, true, null,
                new RuntimeException(), 1, 1);

        assertAll(
                () -> assertNull(satisfied.actual()),
                () -> assertNull(satisfied.result()),
                () -> assertNull(unsatisfied.actual()),
                () -> assertFalse(uncontrolledWithoutActual.hasActual()),
                () -> assertNull(uncontrolledWithoutActual.actual()),
                () -> assertTrue(uncontrolledWithNullActual.hasActual()),
                () -> assertNull(uncontrolledWithNullActual.actual()));
        assertEquals(Long.MIN_VALUE, satisfied(
                "actual", "result", 1, Long.MIN_VALUE).completedNanos());
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

        assertEquals(Attempt.Status.SATISFIED, outcome.status());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertSame(result, outcome.result());
        assertEquals(1, outcome.number());
        assertEquals(2, outcome.completedNanos());
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    void classifiesUnsatisfiedEvaluationsAndPreservesAssertionContext() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        Attempt<Object> outcome = attempt(() -> actual,
                value -> assertionUnsatisfied(
                        "assertion did not pass", assertion));

        assertEquals(Attempt.Status.UNSATISFIED, outcome.status());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals("assertion did not pass", outcome.mismatch());
        assertSame(assertion, outcome.assertionCause());
        assertEquals(1, outcome.number());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableAtItsOrigin(Throwable failure) {
        var conditionCalls = new int[1];

        Attempt<Object> outcome = attempt(
                () -> throwFromSource(failure), value -> {
                    conditionCalls[0]++;
                    return satisfied(value);
                });

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.SOURCE, outcome.origin());
        assertSame(failure, outcome.cause());
        assertFalse(outcome.hasActual());
        assertNull(outcome.actual());
        assertEquals(1, outcome.number());
        assertEquals(0, conditionCalls[0]);
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAndRetainsActual(
            Throwable failure) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(
                () -> actual, value -> throwFromCondition(failure));

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(1, outcome.number());
    }

    @Test
    void classifiesAnEvaluationOwnedUncontrolledCauseAsConditionOrigin() {
        var actual = new Object();
        var failure = new IllegalStateException("built-in failed");

        Attempt<Object> outcome = attempt(() -> actual,
                value -> uncontrolled(failure));

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.CONDITION, outcome.origin());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(NullPointerException.class, outcome.cause().getClass());
        assertEquals("condition returned null Evaluation",
                outcome.cause().getMessage());
        assertEquals(1, conditionCalls[0]);
    }

    @Test
    void thrownSourceInterruptionIsPreservedAndRestored() {
        var interrupted = new InterruptedException("source stopped");

        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            throw interrupted;
        }, Evaluation::satisfied);

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.SOURCE, outcome.origin());
        assertSame(interrupted, outcome.cause());
        assertFalse(outcome.hasActual());
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

        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(Attempt.Origin.CONDITION, outcome.origin());
        assertSame(interrupted, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void fatalSignalsFromSourceEscapeRawAndSkipCondition() {
        var conditionCalls = new int[1];
        var virtualMachineError = new ThrowableFixtures.Fatal("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(
                ThrowableFixtures.Fatal.class,
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
        var virtualMachineError = new ThrowableFixtures.Fatal("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(
                ThrowableFixtures.Fatal.class,
                () -> attempt(() -> actual, value -> {
                    throw virtualMachineError;
                })));
        assertSame(threadDeath, assertThrows(ThreadDeath.class,
                () -> attempt(() -> actual, value -> {
                    throw threadDeath;
                })));
    }

    @Test
    void sourceFlagAfterNormalReturnWinsAndSkipsCondition() {
        var actual = new Object();
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
        assertNull(outcome.result());
        assertNull(outcome.mismatch());
        assertNull(outcome.assertionCause());
    }

    @ParameterizedTest
    @MethodSource("abruptNonInterruptions")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        Attempt<Object> outcome = attempt(() -> {
            currentThread().interrupt();
            return throwFromSource(failure);
        }, Evaluation::satisfied);

        assertEquals(Attempt.Origin.SOURCE, outcome.origin());
        assertSame(failure, outcome.cause());
        assertFalse(outcome.hasActual());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("abruptNonInterruptions")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();

        Attempt<Object> outcome = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return throwFromCondition(failure);
        });

        assertEquals(Attempt.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertSame(actual, outcome.actual());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void abruptFatalThrowableWinsOverCallbackSetFlag() {
        var sourceFatal = new ThrowableFixtures.Fatal("source fatal");
        assertSame(sourceFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> attempt(() -> {
                    currentThread().interrupt();
                    throw sourceFatal;
                }, Evaluation::satisfied)));
        assertTrue(currentThread().isInterrupted());
        interrupted();

        var conditionFatal = new ThrowableFixtures.Fatal("condition fatal");
        assertSame(conditionFatal, assertThrows(ThrowableFixtures.Fatal.class,
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
                new ThrowableFixtures.Checked("checked"),
                new IllegalStateException("runtime"),
                new AssertionError("assertion"),
                new LinkageError("error"));
    }

    private static Stream<Throwable> abruptNonInterruptions() {
        return nonFatalThrowables();
    }

    private static Stream<Arguments> normalConditionReturns() {
        return Stream.of(
                Arguments.of(satisfied(new Object())),
                Arguments.of(unsatisfied("not ready")),
                Arguments.of((Object) null));
    }

    private static Object throwFromSource(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
    }

    private static Evaluation<Object> throwFromCondition(Throwable failure)
            throws Exception {
        throwFromSource(failure);
        throw new AssertionError("unreachable");
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
        assertEquals(Attempt.Status.UNCONTROLLED, outcome.status());
        assertEquals(origin, outcome.origin());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(1, outcome.number());
        assertEquals(InterruptedException.class, outcome.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                outcome.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }
}
