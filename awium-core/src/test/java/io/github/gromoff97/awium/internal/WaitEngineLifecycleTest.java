package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Diagnostic;
import io.github.gromoff97.awium.support.FakeTime;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.observation;
import static java.util.Collections.singletonList;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WaitEngineLifecycleTest {
    private final FakeTime time = new FakeTime(0);
    private final Condition<String, String> run = mock(CALLS_REAL_METHODS);

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runCreationFailuresReturnAnOutcomeBeforeReadingTheSource(boolean fatal) {
        Throwable original = fatal ? new AssertionError("cannot create run") : new IllegalStateException("cannot create run");
        Condition<String, String> definition = mock(CALLS_REAL_METHODS);
        Callable<String> source = mock();
        when(definition.newRun()).thenThrow(original);

        var result = tryAwait(source).until(definition);

        assertSame(original, failure(CONDITION_FAILED, result).cause());
        var attempt = assertInstanceOf(FailedBeforeObservation.class, attempts(result).getFirst());
        assertEquals(CONDITION, attempt.operation());
        verifyNoInteractions(source);
        verify(definition).newRun();
    }

    @Test
    void aMissingRunReturnsTheContractFailureWithoutReadingTheSource() {
        Condition<String, String> definition = mock();
        Callable<String> source = mock();

        var result = tryAwait(source).until(definition);

        assertThat(failure(CONDITION_FAILED, result).cause()).isInstanceOf(NullPointerException.class)
                .hasMessage("condition returned null run");
        verifyNoInteractions(source);
    }

    @Test
    void aMissingEvaluatorReportsTheSameContractAsBuiltInFactories() throws Exception {
        doReturn(null).when(run).newAttempt();

        var result = tryAwait(() -> "actual").until(run);

        assertThat(failure(CONDITION_FAILED, result).cause()).isInstanceOf(NullPointerException.class)
                .hasMessage("evaluator must not be null");
        assertEquals("actual", observation(attempts(result).getFirst()));
        verify(run).newAttempt();
    }

    @Test
    void runCreationUsesTheAcquisitionBudget() throws Throwable {
        Condition<String, String> definition = mock();
        when(definition.newRun()).thenAnswer(ignored -> {
            time.advanceNanos(2);
            return run;
        });
        doReturn(satisfied("selected")).when(run).probe("actual");

        var result = withTime(tryAwait(() -> "actual"), time, time).upTo(ofNanos(1)).until(definition);

        assertThat(failure(TIMEOUT, result).message()).contains("elapsed 2 ns");
        verify(run).probe("actual");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runCreationCannotClearTheCallersExistingInterruption(boolean fails) throws Throwable {
        var original = new IllegalStateException("run creation failed");
        Condition<String, String> definition = mock();
        Callable<String> source = mock();
        when(source.call()).thenReturn("actual");
        doReturn(satisfied("selected")).when(run).probe("actual");
        when(definition.newRun()).thenAnswer(ignored -> {
            interrupted();
            if (fails) throw original;
            return run;
        });
        currentThread().interrupt();

        var result = tryAwait(source).until(definition);

        var failure = failure(INTERRUPTED, result);
        if (fails) assertSame(original, failure.cause());
        else assertInstanceOf(InterruptedException.class, failure.cause());
        assertTrue(currentThread().isInterrupted());
        verifyNoInteractions(source);
        verify(definition).newRun();
        verify(run, never()).newAttempt();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void historyRoutingFailuresRetainTheObservationWithoutAnotherAttempt(boolean accepted) throws Throwable {
        var original = new IllegalStateException("cannot choose a history");
        var measured = new Diagnostic(null, new Reference<>("Progress", 2));
        doReturn(accepted ? satisfied("selected", measured) : unsatisfied(measured.withMismatch("pending")))
                .when(run).probe("actual");
        when(run.historySlot(any())).thenThrow(original);

        var result = tryAwait(() -> "actual").until(run);

        assertSame(original, failure(CONDITION_FAILED, result).cause());
        var attempt = assertInstanceOf(FailedAfterObservation.class, attempts(result).getFirst());
        assertEquals("actual", attempt.observed());
        assertEquals(2, attempt.diagnostic().fields().getFirst().value());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L);
        verify(run).historySlot(any());
        verify(run).probe("actual");
    }

    @Test
    void brokenHistoryRoutingDoesNotReplaceASourceFailure() {
        var original = new IllegalStateException("source failed");
        var routing = new AssertionError("cannot choose a history");
        when(run.historySlot(any())).thenThrow(routing);
        Callable<String> source = () -> { throw original; };

        var result = tryAwait(source).until(run);

        assertSame(original, failure(SOURCE_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(routing);
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, routing.getSuppressed().length);
        assertInstanceOf(FailedBeforeObservation.class, attempts(result).getFirst());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidHistorySlotsReturnAFailureWithoutAdvancingTheAttemptTwice(int slot) throws Throwable {
        doReturn(satisfied("selected")).when(run).probe("actual");
        when(run.historySlot(any())).thenReturn(slot);

        var result = tryAwait(() -> "actual").until(run);

        assertThat(failure(CONDITION_FAILED, result).cause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("history slot must be positive");
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L);
    }

    @Test
    void historyRoutingUsesTheAcquisitionBudget() throws Throwable {
        doReturn(satisfied("selected")).when(run).probe("actual");
        when(run.historySlot(any())).thenAnswer(ignored -> {
            time.advanceNanos(2);
            return 1;
        });

        var result = withTime(tryAwait(() -> "actual"), time, time).upTo(ofNanos(1)).until(run);

        assertThat(failure(TIMEOUT, result).message()).contains("elapsed 2 ns");
    }

    @Test
    void aHistoryCallbackCannotEraseAnAlreadyDetectedInterruption() {
        Callable<String> source = mock();
        when(run.historySlot(any())).thenAnswer(ignored -> {
            interrupted();
            throw new IllegalStateException("routing failed");
        });
        currentThread().interrupt();

        var result = tryAwait(source).until(run);

        assertInstanceOf(InterruptedException.class, failure(INTERRUPTED, result).cause());
        assertTrue(currentThread().isInterrupted());
        assertThat(failure(result).suppressed()).singleElement().isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }

    @Test
    void brokenHistoryDeclarationsKeepEveryRetainedObservation() throws Throwable {
        var original = new IllegalStateException("cannot describe histories");
        var values = List.of("created", "paid", "shipped").iterator();
        doReturn(unsatisfied("waiting"), unsatisfied("waiting"), satisfied("selected")).when(run).probe(any());
        when(run.historySlot(any())).thenReturn(1, 2, 2);
        doThrow(original).when(run).historyConditions(any());

        var result = tryAwait(values::next).every(ofNanos(1)).until(run);

        assertSame(original, failure(CONDITION_FAILED, result).cause());
        assertThat(result.conditions()).hasSize(1);
        assertSame(run, result.conditions().getFirst().condition());
        assertThat(attempts(result)).extracting(attempt -> observation(attempt))
                .containsExactly("created", "paid", "shipped");
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 3L);
        verify(run).historyConditions(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void historyFailuresPreserveTheSourceFailureAndEverySecondaryCause(boolean routingAlsoFails) {
        var original = new IllegalStateException("source failed");
        var routing = new AssertionError("routing failed");
        var declarations = new IllegalArgumentException("history declarations failed");
        if (routingAlsoFails) when(run.historySlot(any())).thenThrow(routing);
        doThrow(declarations).when(run).historyConditions(any());
        Callable<String> source = () -> { throw original; };

        var result = tryAwait(source).until(run);

        assertSame(original, failure(SOURCE_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactlyElementsOf(
                routingAlsoFails ? List.of(routing, declarations) : List.of(declarations));
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, routing.getSuppressed().length);
        assertEquals(0, declarations.getSuppressed().length);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidHistoryDeclarationsReturnAContractFailure(boolean missingList) throws Throwable {
        doReturn(satisfied("selected")).when(run).probe("actual");
        doReturn(missingList ? null : singletonList(null)).when(run).historyConditions(any());

        var result = tryAwait(() -> "actual").until(run);

        assertInstanceOf(NullPointerException.class, failure(CONDITION_FAILED, result).cause());
        assertEquals("actual", observation(attempts(result).getFirst()));
    }

    @Test
    void aDeclarationFailureKeepsTheLastCompactedGroupAndDoesNotInventAPoll() throws Throwable {
        var original = new IllegalStateException("history declarations failed");
        doReturn(unsatisfied("waiting")).when(run).probe("pending");
        doThrow(original).when(run).historyConditions(any());

        var result = withTime(tryAwait(() -> "pending"), time, time)
                .every(ofNanos(1)).upTo(ofNanos(3)).until(run);

        assertSame(original, failure(CONDITION_FAILED, result).cause());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(3L);
        assertThat(result.toString()).contains("Through #3: unsatisfied", "Last attempt #3: failed (CONDITION)",
                "Within: condition histories");
        verify(run, times(3)).probe("pending");
    }

    @Test
    void brokenDeclarationsCannotEraseAnAlreadyDetectedInterruption() {
        var original = new IllegalStateException("history declarations failed");
        Callable<String> source = mock();
        doAnswer(ignored -> {
            interrupted();
            throw original;
        }).when(run).historyConditions(any());
        currentThread().interrupt();

        var result = tryAwait(source).until(run);

        assertInstanceOf(InterruptedException.class, failure(INTERRUPTED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(original);
        assertTrue(currentThread().isInterrupted());
        verifyNoInteractions(source);
    }

    @Test
    void aHistorySlotMustReferToADeclaredCondition() throws Throwable {
        doReturn(satisfied("selected")).when(run).probe("actual");
        when(run.historySlot(any())).thenReturn(2);

        var result = tryAwait(() -> "actual").until(run);

        assertThat(failure(CONDITION_FAILED, result).cause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("history slot exceeds condition count");
        assertEquals("actual", observation(attempts(result).getFirst()));
    }

    @Test
    void declaredHistoriesCanRemainUnreachedBeforeTheCurrentOne() throws Throwable {
        doReturn(satisfied("selected")).when(run).probe("actual");
        when(run.historySlot(any())).thenReturn(3);
        doReturn(List.of(run, run, run)).when(run).historyConditions(any());

        var result = tryAwait(() -> "actual").until(run);

        assertEquals("selected", result.orThrow());
        assertThat(result.conditions()).hasSize(3);
        assertThat(result.conditions().get(0).attempts()).isEmpty();
        assertThat(result.conditions().get(1).attempts()).isEmpty();
        assertThat(result.conditions().get(2).attempts().keySet()).containsExactly(1L);
    }

    @Test
    void historyProjectionFailuresRetainTheObservation() throws Throwable {
        var cause = new IllegalStateException("history projection failed");
        var diagnostic = new Diagnostic(null, new Reference<>("Chosen", "chosen"));
        doReturn(satisfied("chosen", diagnostic)).when(run).probe("actual");
        when(run.historyResult(any())).thenThrow(cause);

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        var attempt = assertInstanceOf(FailedAfterObservation.class, outcome.step().reportedAttempt());
        assertEquals("actual", attempt.observed());
        assertEquals(CONDITION, attempt.operation());
        assertSame(cause, attempt.cause());
        assertSame(diagnostic, attempt.diagnostic());
    }

    @Test
    void aProjectionFailureKeepsTheEarlierMismatchCause() throws Throwable {
        var mismatch = new AssertionError("payment is pending");
        var projection = new IllegalStateException("history projection failed");
        doReturn(unsatisfied("pending", mismatch)).when(run).probe("actual");
        when(run.historyResult(any())).thenThrow(projection);

        var result = tryAwait(() -> "actual").until(run);

        assertSame(projection, failure(CONDITION_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(mismatch);
        assertEquals(0, projection.getSuppressed().length);
        assertEquals(0, mismatch.getSuppressed().length);
    }

    @Test
    void theSameFailureFromTwoHistoryCallbacksIsSuppressedOnce() {
        var original = new IllegalStateException("source failed");
        var metadata = new AssertionError("history unavailable");
        when(run.historySlot(any())).thenThrow(metadata);
        doThrow(metadata).when(run).historyConditions(any());
        Callable<String> source = () -> { throw original; };

        var result = tryAwait(source).until(run);

        assertSame(original, failure(SOURCE_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(metadata);
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    void anEarlierMismatchCauseCanBecomePrimaryWithoutSuppressingItself() throws Throwable {
        var original = new AssertionError("condition is pending");
        var projected = new AssertionError("projected check is pending");
        doReturn(unsatisfied("pending", original)).when(run).probe("actual");
        doReturn(unsatisfied("projected pending", projected)).when(run).historyResult(any());
        when(run.historySlot(any())).thenThrow(original);

        var result = tryAwait(() -> "actual").until(run);

        assertSame(original, failure(CONDITION_FAILED, result).cause());
        assertThat(failure(result).suppressed()).containsExactly(projected);
        assertEquals(0, original.getSuppressed().length);
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 11})
    void historyProjectionMustFinishBeforeTheAcquisitionDeadline(long elapsed) throws Throwable {
        doReturn(satisfied("chosen")).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            time.advanceNanos(elapsed);
            return satisfied("chosen");
        });

        var outcome = assertInstanceOf(Timeout.class, execute(() -> "actual"));
        assertEquals(elapsed, outcome.elapsedNanos());
    }

    @Test
    void sourceFailuresDoNotProjectAHistoryResult() throws Throwable {
        var cause = new IOException("source failed");
        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> { throw cause; }));

        assertSame(cause, outcome.step().reportedAttempt().cause());
        verify(run, never()).historyResult(any());
    }

    @Test
    void probeFailuresDoNotProjectAHistoryResult() throws Throwable {
        var cause = new IOException("check failed");
        doThrow(cause).when(run).probe("actual");

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertSame(cause, outcome.step().reportedAttempt().cause());
        verify(run, never()).historyResult(any());
    }

    @ParameterizedTest
    @CsvSource({"probe, 0", "history, 1"})
    void cancellationStopsTheLifecycle(String interruptAt, int historyCalls) throws Throwable {
        doAnswer(ignored -> {
            if (interruptAt.equals("probe")) currentThread().interrupt();
            return satisfied("chosen");
        }).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            currentThread().interrupt();
            return satisfied("chosen");
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertInstanceOf(InterruptedException.class, outcome.step().reportedAttempt().cause());
        assertTrue(currentThread().isInterrupted());
        verify(run, times(historyCalls)).historyResult(any());
    }

    @Test
    void cancellationDuringHistoryProjectionPreservesTheMismatchCause() throws Throwable {
        var cause = new AssertionError("value is still pending");
        var diagnostic = new Diagnostic("pending", new Reference<>("Progress", 2));
        doReturn(unsatisfied(diagnostic, cause)).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            currentThread().interrupt();
            return satisfied("chosen", diagnostic);
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertSame(cause, outcome.step().reportedAttempt().cause());
        assertSame(diagnostic, outcome.step().reportedAttempt().diagnostic());
    }

    private WaitCompletion execute(Callable<String> source) {
        return new WaitEngine(withTime(config(1, 10, 0), time, time), new AwaitRun<>(source, run, time))
                .waitFor();
    }
}
