package io.github.gromoff97.assertility;

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
        Thread.interrupted();
    }

    @ParameterizedTest
    @MethodSource("actualValues")
    void classifiesSatisfiedEvaluationsAndRetainsTheCurrentActual(Object actual) {
        var result = new Object();
        var calls = new int[2];
        var evaluator = evaluator(() -> {
            calls[0]++;
            return actual;
        }, value -> {
            calls[1]++;
            assertSame(actual, value);
            return Evaluation.satisfied(result);
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(7);

        assertEquals(ObservationOutcome.Status.SATISFIED, outcome.status());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertSame(result, outcome.result());
        assertEquals(7, outcome.attempt());
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    void classifiesUnsatisfiedEvaluationsAndPreservesAssertionContext() {
        var actual = new Object();
        var assertion = new AssertionError("failed");
        var evaluator = evaluator(() -> actual,
                value -> Evaluation.assertionUnsatisfied(
                        "assertion did not pass", assertion));

        ObservationOutcome<Object> outcome = evaluator.evaluate(3);

        assertEquals(ObservationOutcome.Status.UNSATISFIED, outcome.status());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals("assertion did not pass", outcome.mismatch());
        assertSame(assertion, outcome.assertionCause());
        assertEquals(3, outcome.attempt());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableAtItsOrigin(Throwable failure) {
        var conditionCalls = new int[1];
        var evaluator = evaluator(() -> throwFromSource(failure), value -> {
            conditionCalls[0]++;
            return Evaluation.satisfied(value);
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(2);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.SOURCE, outcome.origin());
        assertSame(failure, outcome.cause());
        assertFalse(outcome.hasActual());
        assertNull(outcome.actual());
        assertEquals(2, outcome.attempt());
        assertEquals(0, conditionCalls[0]);
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAndRetainsActual(Throwable failure) {
        var actual = new Object();
        var evaluator = evaluator(() -> actual,
                value -> throwFromCondition(failure));

        ObservationOutcome<Object> outcome = evaluator.evaluate(4);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(4, outcome.attempt());
    }

    @Test
    void classifiesAnEvaluationOwnedUncontrolledCauseAsConditionOrigin() {
        var actual = new Object();
        var failure = new IllegalStateException("built-in failed");
        var evaluator = evaluator(() -> actual,
                value -> Evaluation.uncontrolled(failure));

        ObservationOutcome<Object> outcome = evaluator.evaluate(5);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var evaluator = evaluator(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(6);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.CONDITION, outcome.origin());
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
        var evaluator = evaluator(() -> {
            Thread.currentThread().interrupt();
            throw interrupted;
        }, Evaluation::satisfied);

        ObservationOutcome<Object> outcome = evaluator.evaluate(1);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.SOURCE, outcome.origin());
        assertSame(interrupted, outcome.cause());
        assertFalse(outcome.hasActual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void thrownConditionInterruptionIsPreservedWithActualAndRestored() {
        var actual = new Object();
        var interrupted = new InterruptedException("condition stopped");
        var evaluator = evaluator(() -> actual, value -> {
            Thread.currentThread().interrupt();
            throw interrupted;
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(1);

        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(ObservationOutcome.Origin.CONDITION, outcome.origin());
        assertSame(interrupted, outcome.cause());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void fatalSignalsFromSourceEscapeRawAndSkipCondition() {
        var conditionCalls = new int[1];
        var virtualMachineError = new ThrowableFixtures.Fatal("fatal");
        var sourceEvaluator = evaluator(() -> {
            throw virtualMachineError;
        }, value -> {
            conditionCalls[0]++;
            return Evaluation.satisfied(value);
        });
        var threadDeath = new ThreadDeath();
        var deathEvaluator = evaluator(() -> {
            throw threadDeath;
        }, Evaluation::satisfied);

        assertSame(virtualMachineError, assertThrows(
                ThrowableFixtures.Fatal.class, () -> sourceEvaluator.evaluate(1)));
        assertSame(threadDeath, assertThrows(
                ThreadDeath.class, () -> deathEvaluator.evaluate(1)));
        assertEquals(0, conditionCalls[0]);
    }

    @Test
    void fatalSignalsFromConditionEscapeRaw() {
        var actual = new Object();
        var virtualMachineError = new ThrowableFixtures.Fatal("fatal");
        var sourceEvaluator = evaluator(() -> actual, value -> {
            throw virtualMachineError;
        });
        var threadDeath = new ThreadDeath();
        var deathEvaluator = evaluator(() -> actual, value -> {
            throw threadDeath;
        });

        assertSame(virtualMachineError, assertThrows(
                ThrowableFixtures.Fatal.class, () -> sourceEvaluator.evaluate(1)));
        assertSame(threadDeath, assertThrows(
                ThreadDeath.class, () -> deathEvaluator.evaluate(1)));
    }

    @Test
    void sourceFlagAfterNormalReturnWinsAndSkipsCondition() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var evaluator = evaluator(() -> {
            Thread.currentThread().interrupt();
            return actual;
        }, value -> {
            conditionCalls[0]++;
            return Evaluation.satisfied(value);
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(8);

        assertFlagInterruption(outcome, ObservationOutcome.Origin.SOURCE,
                actual, 8);
        assertEquals(0, conditionCalls[0]);
    }

    @ParameterizedTest
    @MethodSource("normalConditionReturns")
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation(
            Evaluation<Object> evaluation) {
        var actual = new Object();
        var evaluator = evaluator(() -> actual, value -> {
            Thread.currentThread().interrupt();
            return evaluation;
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(9);

        assertFlagInterruption(outcome, ObservationOutcome.Origin.CONDITION,
                actual, 9);
        assertNull(outcome.result());
        assertNull(outcome.mismatch());
        assertNull(outcome.assertionCause());
    }

    @ParameterizedTest
    @MethodSource("abruptNonInterruptions")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var evaluator = evaluator(() -> {
            Thread.currentThread().interrupt();
            return throwFromSource(failure);
        }, Evaluation::satisfied);

        ObservationOutcome<Object> outcome = evaluator.evaluate(10);

        assertEquals(ObservationOutcome.Origin.SOURCE, outcome.origin());
        assertSame(failure, outcome.cause());
        assertFalse(outcome.hasActual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("abruptNonInterruptions")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();
        var evaluator = evaluator(() -> actual, value -> {
            Thread.currentThread().interrupt();
            return throwFromCondition(failure);
        });

        ObservationOutcome<Object> outcome = evaluator.evaluate(11);

        assertEquals(ObservationOutcome.Origin.CONDITION, outcome.origin());
        assertSame(failure, outcome.cause());
        assertSame(actual, outcome.actual());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void abruptFatalThrowableWinsOverCallbackSetFlag() {
        var sourceFatal = new ThrowableFixtures.Fatal("source fatal");
        var sourceEvaluator = evaluator(() -> {
            Thread.currentThread().interrupt();
            throw sourceFatal;
        }, Evaluation::satisfied);

        assertSame(sourceFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> sourceEvaluator.evaluate(1)));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();

        var conditionFatal = new ThrowableFixtures.Fatal("condition fatal");
        var conditionEvaluator = evaluator(Object::new, actual -> {
            Thread.currentThread().interrupt();
            throw conditionFatal;
        });

        assertSame(conditionFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> conditionEvaluator.evaluate(1)));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static Stream<Object> actualValues() {
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

    private static Stream<Evaluation<Object>> normalConditionReturns() {
        return Stream.of(
                Evaluation.satisfied(new Object()),
                Evaluation.unsatisfied("not ready"),
                null);
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

    private static ObservationEvaluator<Object, Object> evaluator(
            AwaitSources.Source<Object> source,
            ConditionRuntime.Evaluator<Object, Object> condition) {
        return new ObservationEvaluator<>(source,
                new ConditionRuntime<>(condition, () -> "test condition", null),
                new InterruptGuard());
    }

    private static void assertFlagInterruption(
            ObservationOutcome<?> outcome,
            ObservationOutcome.Origin origin,
            Object actual,
            long attempt) {
        assertEquals(ObservationOutcome.Status.UNCONTROLLED, outcome.status());
        assertEquals(origin, outcome.origin());
        assertTrue(outcome.hasActual());
        assertSame(actual, outcome.actual());
        assertEquals(attempt, outcome.attempt());
        assertEquals(InterruptedException.class, outcome.cause().getClass());
        assertEquals("caller thread interrupt flag was set",
                outcome.cause().getMessage());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
