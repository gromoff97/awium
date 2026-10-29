package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryFormattingTest {

    @Test
    void repeatedGroupRendersSharedDiagnosticsOnceAndBothObservationBoundaries() {
        var time = new FakeTime(0);
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1))
                .until(equalTo(4).because("worker must finish"));

        assertEquals("""
                Attempts 1–3 (ACQUISITION)
                    Completed after: 0 nanoseconds → 2 nanoseconds
                    Condition: value equals expected
                        Expected: 4
                        Because: worker must finish
                    Result: unsatisfied
                    Mismatch: value was not equal
                    First observed (current value): 1
                    Last observed (current value): 3""", result.attempts().getFirst().toString());
        assertEquals("""
                Attempt 4 (ACQUISITION)
                    Completed after: 3 nanoseconds
                    Condition: value equals expected
                        Expected: 4
                        Because: worker must finish
                    Result: satisfied
                    Observed (current value): 4""", result.attempts().getLast().toString());
        assertEquals(4, calls[0]);
        String report = result.toString();
        assertEquals(1, report.lines().filter(line -> line.contains("Condition:")).count());
        assertTrue(report.contains("Attempts 1–3 (ACQUISITION)"), report);
        assertTrue(report.contains("Attempt 4 (ACQUISITION)"), report);
    }

    @Test
    void completeReportsRenderSharedOperandsOnceAndStandaloneGroupsRemainSelfContained() {
        int[] renders = {0};
        List<String> expected = new ArrayList<>(List.of("ready")) {
            @Override public String toString() { renders[0]++; return super.toString(); }
        };
        var time = new FakeTime(0);
        var values = List.of(List.<String>of(), List.of("ready")).iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1)).until(equalTo(expected));

        assertEquals(0, renders[0]);
        String report = result.toString();
        assertEquals(1, renders[0]);
        assertTrue(report.contains("Result: unsatisfied"), report);
        assertTrue(report.contains("Result: satisfied"), report);
        String last = result.attempts().getLast().toString();
        assertEquals(2, renders[0]);
        assertTrue(last.contains("Condition: value equals expected"), last);
        assertTrue(last.contains("Expected: [ready]"), last);
    }

    @Test
    void capturedHistoryExplainsTheSequenceAndTheAttemptedStage() {
        var time = new FakeTime(0);
        var values = List.of("created", "pending", "pending", "paid").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(equalTo("created"), equalTo("paid").because("payment accepted"))
                        .because("order lifecycle"));

        assertEquals("""
                Attempts 2–3 (ACQUISITION)
                    Completed after: 1 nanosecond → 2 nanoseconds
                    Stage: 2 of 2 (captured 1)
                    Sequence because: order lifecycle
                    Condition: value equals expected
                        Expected: paid
                        Because: payment accepted
                    Result: unsatisfied
                    Mismatch: value was not equal
                    Observed (current value): pending""", result.attempts().get(1).toString());
        String report = result.toString();
        assertEquals(1, report.lines().filter(line -> line.contains("Sequence because:")).count());
        assertEquals(2, report.lines().filter(line -> line.contains("Condition:")).count());
        assertTrue(report.contains("Expected: created"), report);
        assertTrue(report.contains("Expected: paid"), report);
        assertTrue(report.contains("Stage: 2 of 2 (captured 2)"), report);
    }

    @Test
    void returningToAnEarlierConditionRepeatsItsDefinitionInTheReport() {
        var time = new FakeTime(0);
        var ready = equalTo("ready");
        var values = List.of("ready", "retry", "ready").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(ready, equalTo("retry"), ready));

        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.contains("Condition:")).count());
        assertEquals(2, report.lines().filter(line -> line.contains("Expected: ready")).count());
        assertEquals(List.of("ready", "retry", "ready"), result.orThrow());
    }

    @Test
    void aReturnedNullIsDifferentFromAnUnavailableObservation() {
        var time = new FakeTime(0);
        var returnedNull = tryAwait((java.util.concurrent.Callable<String>) () -> null)
                .usingTime(time, time).until(Conditions.isNull);
        var cause = new IllegalStateException("source unavailable");
        var sourceFailed = tryAwait((java.util.concurrent.Callable<String>) () -> { throw cause; })
                .usingTime(time, time).until(Conditions.isNull);

        assertTrue(returnedNull.attempts().getFirst().toString().contains("Observed (current value): null"));
        String failed = sourceFailed.attempts().getFirst().toString();
        assertFalse(failed.contains("Observed (current value):"), failed);
        assertTrue(failed.contains("Observed: unavailable"), failed);
        assertTrue(failed.contains("Result: uncontrolled (SOURCE)"), failed);
        assertTrue(failed.contains("Cause: java.lang.IllegalStateException"), failed);
        assertTrue(failed.contains("Message: source unavailable"), failed);
        assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, sourceFailed).failure().cause());
    }

    @Test
    void valueRenderingIsDeferredAndFailuresCannotReplaceTheWaitOutcome() {
        var time = new FakeTime(0);
        int[] renders = {0};
        Object observed = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw new IllegalArgumentException("unprintable");
            }
        };
        var result = tryAwait(() -> observed).usingTime(time, time)
                .until(condition("ready", actual -> satisfied()));

        assertEquals(0, renders[0]);
        assertSame(observed, result.orThrow());
        String rendered = result.attempts().getFirst().toString();
        assertEquals(1, renders[0]);
        assertTrue(rendered.contains("Observed (current value): <value unavailable: diagnostics failed>"), rendered);
        assertTrue(rendered.contains("Diagnostics failure: java.lang.IllegalArgumentException"), rendered);
        String individual = result.attempts().getFirst().first().toString();
        assertEquals(2, renders[0]);
        assertTrue(individual.startsWith("Attempt (ACQUISITION)\n"), individual);
        assertTrue(individual.contains("Result: satisfied"), individual);
        assertTrue(individual.contains("Observed (current value): <value unavailable: diagnostics failed>"), individual);
        assertSame(observed, result.orThrow());
    }

    @Test
    void multilineReasonsAndArrayValuesRemainReadable() {
        var time = new FakeTime(0);
        var result = tryAwait(() -> new int[]{1, 2}).usingTime(time, time)
                .until(equalTo(new int[]{1, 2}).because("first line\r\nsecond line"));

        String rendered = result.attempts().getFirst().toString();
        assertTrue(rendered.contains("Expected: [1, 2]"), rendered);
        assertTrue(rendered.contains("Because:\n            first line\n            second line"), rendered);
        assertTrue(rendered.contains("Observed (current value): [1, 2]"), rendered);
    }

    @Test
    void repeatedObservationIsRenderedOnceWithoutComparingUserValues() {
        var time = new FakeTime(0);
        int[] renders = {0};
        Object observed = new Object() {
            @Override public String toString() { return "render " + ++renders[0]; }
            @Override public boolean equals(Object other) { throw new AssertionError("unexpected equals"); }
            @Override public int hashCode() { throw new AssertionError("unexpected hashCode"); }
        };
        int[] calls = {0};
        var result = tryAwait(() -> observed).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", actual -> ++calls[0] == 3 ? satisfied() : unsatisfied()));

        assertEquals(0, renders[0]);
        var group = result.attempts().getFirst();
        assertEquals(2, group.count());
        String rendered = group.toString();
        assertEquals(1, renders[0]);
        assertTrue(rendered.contains("Observed (current value): render 1"), rendered);
        assertSame(observed, group.first().observed());
        assertSame(observed, group.last().observed());
    }

    @Test
    void diagnosticResultsDoNotDelegateToUncheckedValueOrThrowableRenderers() {
        var time = new FakeTime(0);
        Object observed = new Object() {
            @Override public String toString() { throw new IllegalStateException("value renderer failed"); }
        };
        var successful = tryAwait(() -> observed).usingTime(time, time)
                .until(condition("ready", actual -> satisfied()));
        var cause = new IllegalArgumentException("original cause") {
            @Override public String toString() { throw new IllegalStateException("cause renderer failed"); }
        };
        var failed = tryAwait((java.util.concurrent.Callable<String>) () -> { throw cause; })
                .usingTime(time, time).until(Conditions.isNotNull);

        String successText = successful.toString();
        String failureText = failed.toString();
        assertTrue(successText.startsWith("Wait satisfied\n    Result: <value unavailable: diagnostics failed>"), successText);
        assertTrue(successText.contains("Attempt 1 (ACQUISITION)"), successText);
        assertTrue(failureText.startsWith("Source retrieval failed"), failureText);
        assertTrue(failureText.contains("original cause"), failureText);
        assertTrue(failureText.contains("Attempt 1 (ACQUISITION)"), failureText);
        String individual = failed.attempts().getFirst().first().toString();
        assertTrue(individual.contains("Result: uncontrolled (SOURCE)"), individual);
        assertTrue(individual.contains("Message: original cause"), individual);
        assertTrue(failed.attempts().getFirst().first().result().toString().contains("original cause"));
        assertTrue(unsatisfied("pending", cause).toString().contains("original cause"));
        var failure = assertInstanceOf(AwaitResult.Failed.class, failed).failure();
        assertEquals(failure.message(), failure.toString());
        assertSame(observed, successful.orThrow());
        assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, failed).failure().cause());
    }

    @Test
    void statusRenderingKeepsItsCategoryWhenTheCauseMessageFails() {
        assertTrue(unsatisfied("pending").toString().contains("pending"));
        var cause = new IllegalStateException() {
            @Override public String getMessage() { throw new AssertionError("message unavailable"); }
        };
        for (AttemptStatus status : List.of(unsatisfied("pending", cause),
                new AttemptStatus.Uncontrolled(AttemptStatus.Operation.CONDITION, cause))) {
            String rendered = status.toString();
            assertTrue(rendered.startsWith(status.getClass().getSimpleName() + "["), rendered);
            assertTrue(rendered.contains(cause.getClass().getName()), rendered);
            assertTrue(rendered.contains("<value unavailable: diagnostics failed>"), rendered);
            assertSame(cause, status.cause());
        }
    }

    @Test
    void liveObservationLabelsCannotBeMistakenForSnapshotsOfEarlierSizes() {
        var time = new FakeTime(0);
        var values = new java.util.ArrayList<Integer>();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if ((++calls[0] & 1) == 1) values.add(calls[0]);
            return values;
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(CollectionConditions.hasSize(4));
        values.add(99);

        assertEquals(3, result.attempts().size());
        for (int i = 0; i < result.attempts().size(); i++) {
            var group = result.attempts().get(i);
            String rendered = group.toString();
            assertTrue(rendered.contains("Mismatch: collection size was " + (i + 1)), rendered);
            assertTrue(rendered.contains("(current value): [1, 3, 5, 99]"), rendered);
            assertSame(values, group.first().observed());
            assertSame(values, group.last().observed());
        }
        String failure = assertInstanceOf(AwaitResult.Failed.class, result).failure().message();
        assertTrue(failure.contains("Actual: [1, 3, 5]"), failure);
    }
}
