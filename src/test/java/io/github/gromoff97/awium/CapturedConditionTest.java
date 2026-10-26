package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.await;
import static io.github.gromoff97.awium.OptionalConditions.await;
import static io.github.gromoff97.awium.MapConditions.await;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.ConditionSupport.captured;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturedConditionTest {

    @Test
    void narrowingSequencesRetainTheirResultTypeAndRetryInBothTerminals() {
        var first = new String("first");
        var second = new String("second");
        Object[] observations = {42, first, 43L, second};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10));
        var start = Conditions.instanceOf(String.class).because("first text");
        var finish = Conditions.exactInstanceOf(String.class).because("second text");

        var values = waiting.until(start, finish);
        AwaitResult<Object, List<String>> attempt = waiting.tryUntil(start, finish);

        assertEquals(5, values.getFirst().length());
        assertSame(first, values.getFirst());
        assertSame(second, values.getLast());
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(4, attempt.totalAttempts());
        assertEquals(8, reads[0]);
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void nestedNarrowingSequencesRetainTheirSelectedResultType() {
        Integer first = 1000;
        Integer second = 2000;
        List<List<Number>> observations = List.of(List.of(1L, first), List.of(second, 2L));
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(ofNanos(1));

        var values = waiting.until(CollectionConditions.single(Conditions.instanceOf(Integer.class)).because("first integer"),
                CollectionConditions.single(Conditions.exactInstanceOf(Integer.class)).because("second integer"));
        AwaitResult<List<Number>, List<Integer>> attempt = waiting.tryUntil(
                CollectionConditions.single(Conditions.instanceOf(Integer.class)),
                CollectionConditions.single(Conditions.exactInstanceOf(Integer.class)));

        assertEquals(1000, values.getFirst().intValue());
        assertSame(first, values.getFirst());
        assertSame(second, values.getLast());
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(2, attempt.totalAttempts());
        assertEquals(4, reads[0]);
    }

    @Test
    void explainedMapValueSequenceCapturesNullAndRefreshesOnlyTheLastStage() {
        var first = java.util.Collections.<String, Number>singletonMap("first", null);
        Number acquired = new BigDecimal("2.00");
        Number persisted = new BigDecimal("3.00");
        var observations = List.of(first, Map.of("last", acquired), Map.of("last", persisted));
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(java.time.Duration.ofNanos(1))
                .persisting(java.time.Duration.ofNanos(1));
        var start = MapConditions.valueFor("first").because("first value");
        var finish = MapConditions.valueFor("last", Conditions.isNotNull).because("last value");

        List<Number> values = waiting.until(start, finish);
        AwaitResult<Map<String, Number>, List<Number>> attempt = waiting.tryUntil(start, finish);

        assertNull(values.getFirst());
        assertSame(persisted, values.getLast());
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(3, attempt.totalAttempts());
        assertEquals(6, reads[0]);
        var acquisition = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.attempts().get(1).outcome());
        var acquiredValues = assertInstanceOf(List.class,
                assertInstanceOf(ConditionResult.Satisfied.class, acquisition.evaluation()).result());
        assertEquals(2, acquiredValues.size());
        assertNull(acquiredValues.getFirst());
        assertSame(acquired, acquiredValues.getLast());
        assertThrows(UnsupportedOperationException.class, acquiredValues::clear);
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

        List<Number> values = waiting.until(firstStage, secondStage, finalStage);
        AwaitResult<Number, List<Number>> captured = waiting.tryUntil(firstStage, secondStage, finalStage);

        assertSame(first, values.get(0));
        assertSame(second, values.get(1));
        assertSame(third, values.get(2));
        var results = assertInstanceOf(List.class, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertSame(first, results.get(0));
        assertSame(second, results.get(1));
        assertSame(third, results.get(2));
        assertEquals(3, captured.totalAttempts());
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

        AwaitResult<List<String>, List<String>> result = timedCollectionAwait(() -> observations.get(reads[0]++),
                config(1, 5, 1), time, time).tryUntil(CollectionConditions.first, CollectionConditions.last);

        var values = assertInstanceOf(List.class, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertSame(first, values.get(0));
        assertSame(persisted, values.get(1));
        var earlier = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, result.attempts().get(1).outcome());
        var earlierValues = assertInstanceOf(List.class,
                assertInstanceOf(ConditionResult.Satisfied.class, earlier.evaluation()).result());
        assertSame(first, earlierValues.get(0));
        assertSame(acquired, earlierValues.get(1));
        assertThrows(UnsupportedOperationException.class, earlierValues::clear);
        assertEquals(3, reads[0]);
        assertEquals(List.of(AttemptRecord.Phase.ACQUISITION, AttemptRecord.Phase.ACQUISITION, AttemptRecord.Phase.PERSISTENCE),
                result.attempts().stream().map(AttemptRecord::phase).toList());
    }

    @Test
    void successfulSequenceReportsAllCapturedStages() {
        var sequence = captured(Conditions.<String>matches(value -> true), matches(value -> true));
        var evaluator = new ConditionAwaitAttempt<>(sequence);
        var first = evaluator.probe("first");
        var last = evaluator.probe("last");
        assertInstanceOf(ConditionResult.Unsatisfied.class, ConditionTestRuntime.evaluated(first));
        assertInstanceOf(ConditionResult.Satisfied.class, ConditionTestRuntime.evaluated(last));
        assertEquals(1, first.progress().capturedStages());
        assertEquals(1, first.progress().evaluatedStageNumber());
        assertEquals(2, last.progress().capturedStages());
        assertEquals(2, last.progress().evaluatedStageNumber());
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
                    }).because("first stage");
                    var second = Conditions.<String>matches(value -> {
                        Thread.currentThread().interrupt();
                        return matching;
                    }).because("second stage");
                    try {
                        AwaitFailure failure;
                        if (capture) {
                            var result = waiting.tryUntil(first, second);
                            failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                            var evaluation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class,
                                    result.attempts().getLast().outcome());
                            var progress = evaluation.progress();
                            assertEquals(stage - 1, progress.capturedStages());
                            assertEquals(stage, progress.evaluatedStageNumber());
                        } else {
                            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(first, second)).failure();
                        }
                        assertEquals(INTERRUPTED, failure.reason());
                        assertInstanceOf(InterruptedException.class, failure.cause());
                        assertTrue(failure.message().contains("Sequence (captured " + (stage - 1) + " of 2)"));
                        assertTrue(failure.message().contains("Because: " + (stage == 1 ? "first" : "second") + " stage"));
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
            var sequence = captured(condition("first stage", (String value) -> satisfied(value)),
                    condition("final stage", (String value) -> {
                        time.advanceNanos(delay);
                        return satisfied(value);
                    }).because("final business reason"));
            var failure = assertFailure(TIMEOUT,
                    () -> timedAwait(() -> "ready", config(1, 5, 0), time, time).until(sequence));
            assertTrue(failure.getMessage().contains("Sequence (captured 2 of 2)"), failure.getMessage());
            assertTrue(failure.getMessage().contains("final stage"), failure.getMessage());
            assertTrue(failure.getMessage().contains("final business reason"), failure.getMessage());
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
        }, config(1, 10, 0), time, time).until(matches(value -> value == 1),
                matches(value -> value == 1), matches(value -> value == 1));

        assertEquals(List.of(1, 1, 1), result);
        assertEquals(3, calls[0]);
        assertEquals(List.of(1L, 1L), time.parkRequests);
    }

    @Test
    void reportsTheNextStageWhenAcquisitionExpiresAfterCapture() {
        var time = new FakeTime(0);

        var result = timedAwait(() -> {
            time.advanceNanos(2);
            return "observed";
        }, config(1, 2, 0), time, time).tryUntil(matches(value -> true), matches(value -> true));
        var outcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, result.attempts().getFirst().outcome()).evaluation());

        assertEquals("waiting for sequence stage 2", outcome.mismatch());
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
                config(1, 10, 0), time, time).until(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED));

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
                config(1, 10, 2), time, time).until(matches(value -> {
                    earlyStageCalls[0]++;
                    return value == early;
                }), matches(value -> {
                    finalStageCalls[0]++;
                    return value != early;
                }));

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
                () -> observations[sourceCalls[0]++], config(1, 10, 10), time, time).until(matches(value -> value == early),
                        matches(value -> value != rejectedFinal)));

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
                config(1, 3, 0), time, time).until(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED)));

        assertEquals(3, calls[0]);
    }

    @Test
    void capturesProjectedObservations() {
        var time = new FakeTime(0);
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = timedAwait(() -> observations[next[0]++].length(),
                config(1, 10, 0), time, time).until(Conditions.equalTo(1), Conditions.equalTo(2));

        assertEquals(List.of(1, 2), result);
    }

    @Test
    void preservingSequenceMixesPlainAndExplainedStages() {
        var time = new FakeTime(0);
        String[] observations = {"alpha", "omega"};
        int[] next = {0};

        List<String> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(matches((String value) -> value.startsWith("a")),
                matches((String value) -> value.endsWith("a")).because("final state"));

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
                time, time).until(condition("first null", ConditionResult::satisfied),
                condition("second null", ConditionResult::satisfied));

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    void terminalSnapshotsTheArrayBeforePollingAndKeepsTheResultType() {
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

        List<Integer> values = waiting.until(stages);
        assertEquals(List.of(1, 2), values);

        calls[0] = 0;
        stages[0] = matches(value -> value == 1);
        stages[1] = matches(value -> value == 2);
        AwaitResult<Integer, List<Integer>> captured = waiting.tryUntil(stages);
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertEquals(2, captured.totalAttempts());
        assertThrows(IllegalArgumentException.class, () -> waiting.until(java.util.Arrays.copyOf(stages, 0)));
        assertThrows(IllegalArgumentException.class, () -> waiting.tryUntil(java.util.Arrays.copyOf(stages, 0)));
        assertEquals(2, calls[0]);

        calls[0] = 0;
        stages[0] = matches(value -> value == 1);
        var singleton = waiting.until(java.util.Arrays.copyOf(stages, 1));
        assertEquals(1, singleton.size());
        assertEquals(1, singleton.getFirst().intValue());
    }

    @Test
    void selectedSequenceCapturesSelectedValues() {
        var time = new FakeTime(0);
        List<Optional<String>> observations = List.of(Optional.of("first"),
                Optional.of("second"));
        int[] next = {0};

        List<String> result = await(() -> observations.get(next[0]++))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10))
                .until(present, present);

        assertEquals(List.of("first", "second"), result);
    }

    @Test
    void sequenceStartsFreshAfterSuccessAndTimeout() {
        Condition.Typed<Payment, List<Payment>> lifecycle = captured(matches(value -> value.status() == Status.CREATED),
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
            Condition.Typed<Payment, List<Payment>> lifecycle, long timeout) {
        var time = new FakeTime(0);
        int[] next = {0};
        return timedAwait(() -> observations[Math.min(next[0]++, observations.length - 1)],
                config(1, timeout, 0), time, time).until(lifecycle);
    }
}
