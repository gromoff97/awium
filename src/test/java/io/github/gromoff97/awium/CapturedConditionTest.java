package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.evaluated;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturedConditionTest {

    @Test
    void mixedChecksAndSelectionsCaptureEachCompleteObservation() {
        var first = List.of(new Payment(Status.CREATED, "first"));
        var second = List.of(new Payment(Status.PENDING, "second"));
        var third = List.of(new Payment(Status.FINISHED, "third"));
        var observations = List.of(first, second, third);
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(ofNanos(1));

        List<List<Payment>> values = waiting.until(captured(
                CollectionConditions.hasSize(1),
                CollectionConditions.singleElement,
                CollectionConditions.singleElement(Conditions.instanceOf(Payment.class))));
        AwaitResult<List<Payment>, List<List<Payment>>> result = diagnostic(waiting).until(captured(
                CollectionConditions.hasSize(1),
                CollectionConditions.singleElement,
                CollectionConditions.singleElement(Conditions.instanceOf(Payment.class))));

        assertSame(first, values.getFirst());
        assertSame(second, values.get(1));
        assertSame(third, values.getLast());
        List<List<Payment>> captured = result.orThrow();
        assertEquals(values, captured);
        assertSame(captured, result.orThrow());
        assertEquals(observations, result.attempts().stream().map(group -> group.last()).map(HistoryEntry::observed).toList());
        assertTrue(result.attempts().stream().map(group -> group.last()).allMatch(attempt -> attempt.result() instanceof AttemptStatus.Satisfied));
        assertEquals(6, reads[0]);
    }

    @Test
    void differentDeclaredStageResultsDoNotChangeTheCaptureType() {
        Object text = new String("ready");
        Object number = Integer.valueOf(1234);
        Object[] observations = {false, text, false, number};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .usingTime(time, time).every(ofNanos(1));

        List<Object> values = waiting.until(captured(
                Conditions.instanceOf(String.class), Conditions.instanceOf(Integer.class)));
        AwaitResult<Object, List<Object>> result = diagnostic(waiting).until(captured(
                Conditions.instanceOf(String.class), Conditions.instanceOf(Integer.class)));

        assertSame(text, values.getFirst());
        assertSame(number, values.getLast());
        assertEquals(values, result.orThrow());
        assertEquals(4, result.attempts().size());
        assertEquals(8, reads[0]);
    }

    @Test
    void reusableCaptureKeepsTheCompleteSourceTypeWithoutExplicitArguments() {
        var payment = new Payment(Status.FINISHED, "paid");
        var payments = new java.util.ArrayList<>(List.of(payment));
        var time = new FakeTime(0);
        var checks = captured(Conditions.isNotNull, Conditions.isNotNull).because("observations");
        var selections = captured(CollectionConditions.singleElement, CollectionConditions.singleElement).because("elements");

        var sources = await(() -> payments).usingTime(time, time).every(ofNanos(1)).until(checks);
        java.util.ArrayList<Payment> original = sources.getFirst();
        assertSame(payments, original);
        assertEquals("paid", original.getFirst().detail());

        var selected = await(() -> payments).usingTime(time, time).every(ofNanos(1)).until(selections);
        assertEquals("paid", selected.getFirst().getFirst().detail());
        var strings = await(() -> List.of("ready")).usingTime(time, time).every(ofNanos(1)).until(selections);
        assertEquals(5, strings.getFirst().getFirst().length());

        var optionals = await(() -> Optional.of(payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(present, present).because("present values"));
        assertEquals("paid", optionals.getFirst().orElseThrow().detail());
        var mapped = await(() -> Map.of("id", payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(MapConditions.valueFor("id"), MapConditions.valueFor("id")).because("map values"));
        assertEquals("paid", mapped.getFirst().get("id").detail());
        var entries = await(() -> Map.of("id", payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(MapConditions.entryFor("id"), MapConditions.singleEntry).because("entries"));
        assertEquals("paid", entries.getFirst().get("id").detail());
        var narrowed = await(() -> (Object) payment).usingTime(time, time).every(ofNanos(1))
                .until(captured(Conditions.instanceOf(Payment.class), Conditions.instanceOf(Payment.class)).because("payments"));
        assertSame(payment, narrowed.getFirst());

        var single = await(() -> Optional.of(payment)).usingTime(time, time).until(captured(present));
        assertEquals(1, single.size());
        assertSame(payment, single.getFirst().orElseThrow());
    }

    @Test
    void failureBeforeTheFirstCheckIdentifiesItsStageWithoutInventingAnEvaluation() {
        for (boolean interrupted : List.of(false, true)) {
            var first = Conditions.equalTo("ready");
            var sequence = captured(first, Conditions.equalTo("done")).because("lifecycle");
            var cause = new IllegalStateException("source unavailable");
            int[] reads = {0};
            Callable<String> source = () -> {
                reads[0]++;
                throw cause;
            };
            try {
                if (interrupted) Thread.currentThread().interrupt();
                var result = tryAwait(source).until(sequence);
                var attempt = result.attempts().getFirst().last();
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

                assertEquals(1, result.attempts().size());
                assertSame(first, attempt.condition());
                assertSame(sequence, attempt.progress().sequence());
                assertEquals(1, attempt.progress().stageNumber());
                assertEquals(0, attempt.progress().capturedStages());
                assertEquals(interrupted ? 0 : 1, reads[0]);
                if (interrupted) {
                    uncontrolled(AttemptStatus.Operation.WAITING, attempt.result());
                    assertEquals(INTERRUPTED, failure.reason());
                } else {
                    uncontrolled(AttemptStatus.Operation.SOURCE, attempt.result());
                    assertEquals(SOURCE_FAILED, failure.reason());
                    assertSame(cause, failure.cause());
                }
                assertTrue(failure.message().contains("Because: lifecycle"), failure.message());
                assertTrue(failure.message().contains("Stage: 1 of 2"), failure.message());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void sourceAndWaitingFailuresRetainProgressInBothPhasesAndTerminals() {
        for (boolean capture : List.of(false, true)) {
            for (var phase : HistoryEntry.Phase.values()) {
                int completedStages = phase == HistoryEntry.Phase.ACQUISITION ? 1 : 2;
                for (var reason : List.of(SOURCE_FAILED, WAIT_FAILED)) {
                    var time = new FakeTime(0);
                    var cause = new IllegalStateException("after a completed stage");
                    int[] reads = {0};
                    var waiting = await(() -> {
                        if (++reads[0] > completedStages && reason == SOURCE_FAILED) throw cause;
                        return reads[0] == 1 ? "ready" : "done";
                    }).usingTime(time, nanos -> {
                        if (reads[0] == completedStages && reason == WAIT_FAILED) throw cause;
                        time.accept(nanos);
                    }).every(ofNanos(1)).persisting(ofNanos(2));
                    var first = Conditions.equalTo("ready");
                    var second = Conditions.equalTo("done");
                    AwaitFailure failure;
                    if (capture) {
                        var result = diagnostic(waiting).until(captured(first, second));
                        failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                        var attempt = result.attempts().getLast().last();
                        var progress = attempt.progress();
                        assertEquals(phase, attempt.phase());
                        assertEquals(completedStages, progress.capturedStages());
                        assertEquals(2, progress.stageNumber());
                        assertSame(second, attempt.condition());
                        assertSame(result.attempts().getFirst().last().progress().sequence(), progress.sequence());
                        assertSame(first, result.attempts().getFirst().last().condition());
                    } else {
                        failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(captured(first, second))).failure();
                    }

                    assertEquals(reason, failure.reason());
                    assertSame(cause, failure.cause());
                    assertTrue(failure.message().contains("Sequence (captured " + completedStages + " of 2)"), failure.message());
                    assertTrue(failure.message().contains("Stage: 2 of 2"), failure.message());
                }
            }
        }
    }

    @Test
    void sourceAndWaitingInterruptionsRetainProgressInBothPhases() {
        for (var phase : HistoryEntry.Phase.values()) {
            int completedStages = phase == HistoryEntry.Phase.ACQUISITION ? 1 : 2;
            for (boolean interruptInSource : List.of(false, true)) {
                var time = new FakeTime(0);
                int[] reads = {0};
                var waiting = await(() -> {
                    if (++reads[0] > completedStages && interruptInSource) Thread.currentThread().interrupt();
                    return reads[0] == 1 ? "ready" : "done";
                }).usingTime(time, nanos -> {
                    if (reads[0] == completedStages && !interruptInSource) Thread.currentThread().interrupt();
                    time.accept(nanos);
                }).every(ofNanos(1)).persisting(ofNanos(2));
                try {
                    var first = Conditions.equalTo("ready");
                    var second = Conditions.equalTo("done");
                    var result = diagnostic(waiting).until(captured(first, second));
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                    var attempt = result.attempts().getLast().last();
                    assertEquals(INTERRUPTED, failure.reason());
                    assertEquals(phase, attempt.phase());
                    assertEquals(completedStages, attempt.progress().capturedStages());
                    assertEquals(2, attempt.progress().stageNumber());
                    assertSame(second, attempt.condition());
                    assertSame(result.attempts().getFirst().last().progress().sequence(), attempt.progress().sequence());
                    assertTrue(failure.message().contains("Sequence (captured " + completedStages + " of 2)"));
                    assertTrue(failure.message().contains("Stage: 2 of 2"), failure.message());
                    assertEquals(1, result.attempts().getFirst().last().progress().capturedStages());
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void narrowingSequencesCaptureTheSourceAndRetryInBothTerminals() {
        var first = new String("first");
        var second = new String("second");
        Object[] observations = {42, first, 43L, second};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10));
        var start = Conditions.instanceOf(String.class);
        var finish = Conditions.exactlyInstanceOf(String.class);

        var values = waiting.until(captured(start, finish));
        AwaitResult<Object, List<Object>> attempt = diagnostic(waiting).until(captured(start, finish));

        assertSame(first, values.getFirst());
        assertSame(second, values.getLast());
        assertEquals(values, attempt.orThrow());
        assertEquals(4, attempt.attempts().size());
        assertInstanceOf(AttemptStatus.Satisfied.class, attempt.attempts().get(1).last().result());
        assertSame(first, attempt.attempts().get(1).last().observed());
        assertEquals(8, reads[0]);
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void nestedNarrowingSequencesRetainTheirOriginalContainers() {
        Integer first = 1000;
        Integer second = 2000;
        List<List<Number>> observations = List.of(List.of(first), List.of(second));
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(ofNanos(1));

        var values = waiting.until(captured(CollectionConditions.singleElement(Conditions.instanceOf(Integer.class)),
                CollectionConditions.singleElement(Conditions.exactlyInstanceOf(Integer.class))));
        AwaitResult<List<Number>, List<List<Number>>> attempt = diagnostic(waiting).until(captured(
                CollectionConditions.singleElement(Conditions.instanceOf(Integer.class)),
                CollectionConditions.singleElement(Conditions.exactlyInstanceOf(Integer.class))));

        assertEquals(1000, values.getFirst().getFirst().intValue());
        assertSame(observations.getFirst(), values.getFirst());
        assertSame(observations.getLast(), values.getLast());
        assertEquals(values, attempt.orThrow());
        assertEquals(2, attempt.attempts().size());
        assertEquals(4, reads[0]);
    }

    @Test
    void mapValueSequenceKeepsNullableObservationsAndRefreshesOnlyTheLastStage() {
        var first = java.util.Collections.<String, Number>singletonMap("first", null);
        Number acquired = new BigDecimal("2.00");
        Number persisted = new BigDecimal("3.00");
        var observations = List.of(first, Map.of("last", acquired), Map.of("last", persisted));
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(java.time.Duration.ofNanos(1))
                .persisting(java.time.Duration.ofNanos(1));
        var start = MapConditions.valueFor("first");
        var finish = MapConditions.valueFor("last", Conditions.isNotNull);

        List<Map<String, Number>> values = waiting.until(captured(start, finish));
        AwaitResult<Map<String, Number>, List<Map<String, Number>>> attempt = diagnostic(waiting).until(captured(start, finish));

        assertSame(first, values.getFirst());
        assertNull(values.getFirst().get("first"));
        assertSame(persisted, values.getLast().get("last"));
        assertEquals(values, attempt.orThrow());
        assertEquals(3, attempt.attempts().size());
        assertEquals(6, reads[0]);
        var acquisition = evaluated(attempt.attempts().get(1).last());
        assertInstanceOf(AttemptStatus.Satisfied.class, acquisition.result());
        assertSame(acquired, acquisition.observed().get("last"));
        var firstAttempt = evaluated(attempt.attempts().getFirst().last());
        assertInstanceOf(AttemptStatus.Satisfied.class, firstAttempt.result());
        assertSame(first, firstAttempt.observed());
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void expectedSequencesCaptureTheOriginalObservationsInBothTerminals() {
        Number first = new BigDecimal("1.00");
        Number second = new BigInteger("2");
        Number third = 1024L;
        Number[] observations = {first, second, third, first, second, third};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = timedAwait(() -> observations[reads[0]++], config(1, 5, 0), time, time);
        var firstStage = Conditions.equalTo(new BigDecimal("1.00"));
        var secondStage = Conditions.equalTo(new BigInteger("2"));
        var finalStage = Conditions.equalTo(1024L);

        var sequence = captured(firstStage, secondStage, finalStage).because("numeric states");
        List<Number> values = waiting.until(sequence);
        AwaitResult<Number, List<Number>> captured = diagnostic(waiting).until(sequence);

        assertSame(first, values.get(0));
        assertSame(second, values.get(1));
        assertSame(third, values.get(2));
        List<Number> results = captured.orThrow();
        assertSame(first, results.get(0));
        assertSame(second, results.get(1));
        assertSame(third, results.get(2));
        assertEquals(3, captured.attempts().size());
        assertEquals(6, reads[0]);
    }

    @Test
    void diagnosticSelectedSequenceRefreshesOnlyItsLastValueDuringPersistence() {
        var first = new String("first");
        var acquired = new String("acquired");
        var persisted = new String("persisted");
        var observations = List.of(List.of(first, "ignored"), List.of("ignored", acquired), List.of("ignored", persisted));
        int[] reads = {0};
        var time = new FakeTime(0);

        AwaitResult<List<String>, List<List<String>>> result = tryAwait(() -> observations.get(reads[0]++))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).persisting(ofNanos(1))
                .until(captured(CollectionConditions.firstElement, CollectionConditions.lastElement));

        var values = result.orThrow();
        assertSame(observations.getFirst(), values.get(0));
        assertSame(observations.getLast(), values.get(1));
        var earlier = evaluated(result.attempts().get(1).last());
        assertInstanceOf(AttemptStatus.Satisfied.class, earlier.result());
        assertSame(acquired, earlier.observed().getLast());
        var firstAttempt = evaluated(result.attempts().getFirst().last());
        assertInstanceOf(AttemptStatus.Satisfied.class, firstAttempt.result());
        assertSame(first, firstAttempt.observed().getFirst());
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertEquals(3, reads[0]);
        assertEquals(List.of(HistoryEntry.Phase.ACQUISITION, HistoryEntry.Phase.ACQUISITION, HistoryEntry.Phase.PERSISTENCE),
                result.attempts().stream().map(group -> group.last()).map(HistoryEntry::phase).toList());
    }

    @Test
    void historyRecordsTheCheckedStageAndItsActualResult() {
        var time = new FakeTime(0);
        var source = List.of("created", "created", "processing", "completed").iterator();
        var first = Conditions.equalTo("created");
        var second = Conditions.equalTo("processing");
        var third = Conditions.equalTo("completed");
        var sequence = captured(first, second, third).because("lifecycle");
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).until(sequence);

        var attempts = result.attempts().stream().map(group -> group.last()).toList();
        assertEquals(4, result.attempts().size());
        assertEquals(List.of(1, 2, 2, 3), attempts.stream().map(attempt -> attempt.progress().stageNumber()).toList());
        assertEquals(List.of(1, 1, 2, 3), attempts.stream().map(attempt -> attempt.progress().capturedStages()).toList());
        assertEquals(List.of(first, second, second, third), attempts.stream().map(HistoryEntry::condition).toList());
        for (int index = 0; index < attempts.size(); index++) {
            var attempt = attempts.get(index);
            assertSame(sequence, attempt.progress().sequence());
            assertEquals(3, attempt.progress().sequence().stages().size());
            assertTrue(attempt.hasObservation());
            assertTrue(attempt.observed().length() > 0);
            if (index == 1) {
                assertInstanceOf(AttemptStatus.Unsatisfied.class, attempt.result());
            } else {
                assertInstanceOf(AttemptStatus.Satisfied.class, attempt.result());
            }
        }

        assertEquals(List.of("created", "processing", "completed"),
                result.orThrow());
    }

    @Test
    void interruptedStageIsNotCapturedAfterANormalCallbackReturn() {
        for (boolean capture : List.of(false, true)) {
            for (int stage : new int[] {1, 2}) {
                for (boolean matching : List.of(false, true)) {
                    var time = new FakeTime(0);
                    int[] sources = {0};
                    var waiting = timedAwait(() -> {
                        sources[0]++;
                        return "ready";
                    }, config(1, 3, 0), time, time);
                    var first = Conditions.<String>matches(value -> {
                        if (stage == 1) {
                            Thread.currentThread().interrupt();
                            return matching;
                        }
                        return true;
                    });
                    var second = Conditions.<String>matches(value -> {
                        Thread.currentThread().interrupt();
                        return matching;
                    });
                    try {
                        AwaitFailure failure;
                        if (capture) {
                            var result = diagnostic(waiting).until(captured(first, second));
                            failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                            uncontrolled(AttemptStatus.Operation.CONDITION, result.attempts().getLast().last().result());
                            var progress = result.attempts().getLast().last().progress();
                            assertEquals(stage - 1, progress.capturedStages());
                            assertEquals(stage, progress.stageNumber());
                        } else {
                            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(captured(first, second))).failure();
                        }
                        assertEquals(INTERRUPTED, failure.reason());
                        assertInstanceOf(InterruptedException.class, failure.cause());
                        assertTrue(failure.message().contains("Sequence (captured " + (stage - 1) + " of 2)"));
                        assertTrue(failure.message().contains("Stage: " + stage + " of 2"), failure.message());
                        assertEquals(stage, sources[0]);
                        assertTrue(Thread.currentThread().isInterrupted());
                    } finally {
                        Thread.interrupted();
                    }
                }
            }
        }
    }

    @Test
    void lateSuccessfulSequenceKeepsTheFinalStageDiagnostics() {
        for (long delay : new long[]{4, 5}) {
            var time = new FakeTime(0);
            var sequence = captured(condition("first stage", (String value) -> satisfied()),
                    condition("final stage", (String value) -> {
                        time.advanceNanos(delay);
                        return satisfied();
                    }));
            var failure = assertFailure(TIMEOUT,
                    () -> timedAwait(() -> "ready", config(1, 5, 0), time, time).until(sequence));
            assertTrue(failure.getMessage().contains("Sequence (captured 2 of 2)"), failure.getMessage());
            assertTrue(failure.getMessage().contains("Stage: 2 of 2"), failure.getMessage());
            assertTrue(failure.getMessage().contains("final stage"), failure.getMessage());
        }
    }

    private record Payment(Status status, String detail) {}

    private enum Status { CREATED, PENDING, FINISHED }

    @Test
    void capturesExactlyOnePredicateStagePerPollingAttempt() {
        var time = new FakeTime(0);
        int[] calls = {0};

        List<Integer> result = timedAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, 10, 0), time, time).until(captured(matches(value -> value == 1),
                matches(value -> value == 1), matches(value -> value == 1)));

        assertEquals(List.of(1, 1, 1), result);
        assertEquals(3, calls[0]);
        assertEquals(List.of(1L, 1L), time.parkRequests);
    }

    @Test
    void reportsTheNextStageWhenAcquisitionExpiresAfterCapture() {
        var time = new FakeTime(0);

        var result = timedTryAwait(() -> {
            time.advanceNanos(2);
            return "observed";
        }, config(1, 2, 0), time, time).until(captured(matches(value -> true), matches(value -> true)));
        var outcome = assertInstanceOf(AttemptStatus.Satisfied.class,
                evaluated(result.attempts().getFirst().last()).result());

        assertEquals("observed", result.attempts().getFirst().last().observed());
        assertEquals(1, result.attempts().getFirst().last().progress().stageNumber());
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("Next stage: 2 of 2"), failure.message());
    }

    @Test
    void capturesAChangingSourceInStrictOrder() {
        var time = new FakeTime(0);
        var created = new Payment(Status.CREATED, "accepted");
        var pending = new Payment(Status.PENDING, "settling");
        var finished = new Payment(Status.FINISHED, "paid");
        Payment[] observations = {created, pending, finished};
        int[] next = {0};

        List<Payment> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED)));

        assertEquals(List.of(created, pending, finished), result);
    }

    @Test
    void persistenceReevaluatesAndRefreshesOnlyTheFinalStage() {
        var time = new FakeTime(0);
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String firstPersistedFinal = new String("first persisted final");
        String persistedFinal = new String("final persisted final");
        String[] observations = {early, acquiredFinal, firstPersistedFinal, persistedFinal};
        int[] sourceCalls = {0};
        int[] earlyStageCalls = {0};
        int[] finalStageCalls = {0};

        List<String> result = timedAwait(() -> observations[sourceCalls[0]++],
                config(1, 10, 2), time, time).until(captured(matches(value -> {
                    earlyStageCalls[0]++;
                    return value == early;
                }), matches(value -> {
                    finalStageCalls[0]++;
                    return value != early;
                })));

        assertSame(early, result.get(0));
        assertSame(persistedFinal, result.get(1));
        assertEquals(4, sourceCalls[0]);
        assertEquals(1, earlyStageCalls[0]);
        assertEquals(3, finalStageCalls[0]);
        assertEquals(List.of(1L, 1L, 1L), time.parkRequests);
    }

    @Test
    void finalStageMismatchDuringPersistenceFailsImmediately() {
        var time = new FakeTime(0);
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String rejectedFinal = new String("rejected final");
        String[] observations = {early, acquiredFinal, rejectedFinal};
        int[] sourceCalls = {0};

        assertFailure(PERSISTENCE_FAILED, () -> timedAwait(
                () -> observations[sourceCalls[0]++], config(1, 10, 10), time, time).until(captured(matches(value -> value == early),
                        matches(value -> value != rejectedFinal))));

        assertEquals(3, sourceCalls[0]);
        assertEquals(List.of(1L, 1L), time.parkRequests);
    }

    @Test
    void doesNotSkipAMissedIntermediateStage() {
        var time = new FakeTime(0);
        var created = new Payment(Status.CREATED, "accepted");
        var finished = new Payment(Status.FINISHED, "paid");
        int[] calls = {0};

        assertFailure(TIMEOUT, () -> timedAwait(() ->
                calls[0]++ == 0 ? created : finished,
                config(1, 3, 0), time, time).until(captured(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED))));

        assertEquals(3, calls[0]);
    }

    @Test
    void capturesProjectedObservations() {
        var time = new FakeTime(0);
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = timedAwait(() -> observations[next[0]++].length(),
                config(1, 10, 0), time, time).until(captured(Conditions.equalTo(1), Conditions.equalTo(2)));

        assertEquals(List.of(1, 2), result);
    }

    @Test
    void preservingSequenceCapturesEveryStage() {
        var time = new FakeTime(0);
        String[] observations = {"alpha", "omega"};
        int[] next = {0};

        List<String> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(matches((String value) -> value.startsWith("a")),
                matches((String value) -> value.endsWith("a"))));

        assertEquals(List.of("alpha", "omega"), result);
    }

    @Test
    void validatesEveryStageBeforeEvaluation() {
        PreservingCondition<String> preserving = matches(value -> true);

        assertThrows(NullPointerException.class,
                () -> captured((PreservingCondition<String>) null, preserving));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured((PreservingCondition<String>[]) null));
    }

    @Test
    void returnsAnImmutableCapturedCopyThatAllowsNull() {
        var time = new FakeTime(0);

        List<String> result = timedAwait((Callable<String>) () -> null, config(1, 10, 0),
                time, time).until(captured(condition("first null", ignored -> ConditionResult.satisfied()),
                condition("second null", ignored -> ConditionResult.satisfied())));

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    void captureSnapshotsTheArrayBeforePollingAndKeepsTheResultType() {
        var time = new FakeTime(0);
        @SuppressWarnings("unchecked")
        PreservingCondition<Integer>[] stages = (PreservingCondition<Integer>[]) new PreservingCondition<?>[]{
                matches((Integer value) -> value == 1),
                matches((Integer value) -> value == 2)};
        int[] calls = {0};
        var waiting = await(() -> {
            stages[0] = matches(value -> false);
            stages[1] = matches(value -> false);
            return ++calls[0];
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5));

        var sequence = captured(stages);
        java.util.Arrays.fill(stages, matches((Integer value) -> false));
        List<Integer> values = waiting.until(sequence);
        assertEquals(List.of(1, 2), values);

        calls[0] = 0;
        AwaitResult<Integer, List<Integer>> captured = diagnostic(waiting).until(sequence);
        assertEquals(values, captured.orThrow());
        assertEquals(2, captured.attempts().size());
        assertThrows(IllegalArgumentException.class, () -> captured(java.util.Arrays.copyOf(stages, 0)));
        assertEquals(2, calls[0]);

        calls[0] = 0;
        stages[0] = matches(value -> value == 1);
        var singleton = waiting.until(captured(java.util.Arrays.copyOf(stages, 1)));
        assertEquals(1, singleton.size());
        assertEquals(1, singleton.getFirst().intValue());
    }

    @Test
    void selectedSequenceCapturesOriginalObservations() {
        var time = new FakeTime(0);
        List<Optional<String>> observations = List.of(Optional.of("first"),
                Optional.of("second"));
        int[] next = {0};

        List<Optional<String>> result = await(() -> observations.get(next[0]++))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10))
                .until(captured(present, present));

        assertEquals(observations, result);
    }

    @Test
    void sequenceStartsFreshAfterSuccessAndTimeout() {
        Condition.CapturedSource<Condition.InputCondition<Payment>> lifecycle = captured(matches(value -> value.status() == Status.CREATED),
                matches(value -> value.status() == Status.FINISHED));
        var firstCreated = new Payment(Status.CREATED, "first execution");
        var nextCreated = new Payment(Status.CREATED, "next execution");
        var firstFinished = new Payment(Status.FINISHED, "first completion");
        var nextFinished = new Payment(Status.FINISHED, "next completion");
        var pending = new Payment(Status.PENDING, "waiting");

        assertEquals(List.of(firstCreated, firstFinished), runLifecycle(
                new Payment[]{firstCreated, firstFinished}, lifecycle, 10));
        assertEquals(List.of(nextCreated, nextFinished), runLifecycle(
                new Payment[]{nextCreated, nextFinished}, lifecycle, 10));
        assertFailure(TIMEOUT, () -> runLifecycle(
                new Payment[]{firstCreated, pending, pending}, lifecycle, 3));
        assertEquals(List.of(nextCreated, firstFinished), runLifecycle(
                new Payment[]{nextCreated, firstFinished}, lifecycle, 10));
    }

    private static List<Payment> runLifecycle(Payment[] observations,
            Condition.CapturedSource<Condition.InputCondition<Payment>> lifecycle, long timeout) {
        var time = new FakeTime(0);
        int[] next = {0};
        return timedAwait(() -> observations[Math.min(next[0]++, observations.length - 1)],
                config(1, timeout, 0), time, time).until(lifecycle);
    }
}
