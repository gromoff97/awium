package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedOptionalAwait;
import static io.github.gromoff97.awium.CapturedEvaluator.captured;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturedConditionTest {

    @Test
    void heterogeneousConditionsCaptureTheirCommonResultType() {
        var time = new FakeTime(0);
        var waiting = Await.await(() -> "ready").usingTime(time, time);
        Condition<Object, Object> common = Conditions.equalTo("ready").because("common model");

        Object value = waiting.until(common);
        List<Number> numbers = waiting.until(Conditions.yields(String::length), Conditions.yields(text -> (long) text.length()));
        AwaitResult<String, List<Number>> numberAttempts = waiting.tryUntil(
                Conditions.yields(String::length), Conditions.yields(text -> (long) text.length()));
        List<Object> mixed = waiting.until(common, Conditions.yields(String::length));
        AwaitResult<String, List<Object>> mixedAttempts = waiting.tryUntil(common, Conditions.yields(String::length));

        assertEquals("ready", value);
        assertEquals(List.of(5, 5L), numbers);
        assertEquals(numbers, assertInstanceOf(AwaitResult.Satisfied.class, numberAttempts).result());
        assertEquals(List.of("ready", 5), mixed);
        assertEquals(mixed, assertInstanceOf(AwaitResult.Satisfied.class, mixedAttempts).result());
    }

    @Test
    void explainedMapValueSequenceCapturesNullAndRefreshesOnlyTheLastStage() {
        var first = java.util.Collections.<String, Number>singletonMap("first", null);
        Number acquired = new BigDecimal("2.00");
        Number persisted = new BigDecimal("3.00");
        var observations = List.of(first, Map.of("last", acquired), Map.of("last", persisted));
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = Await.await(() -> observations.get(reads[0]++ % observations.size()))
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
        Condition.PreservingCondition<Object> firstStage = Conditions.equalTo(new BigDecimal("1.00"));
        Condition.PreservingCondition<Object> secondStage = Conditions.equalTo(new BigInteger("2"));
        Condition.PreservingCondition<Object> finalStage = Conditions.equalTo(1024L);

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
        assertEquals(3, reads[0]);
        assertEquals(List.of(AwaitAttempt.Phase.ACQUISITION, AwaitAttempt.Phase.ACQUISITION, AwaitAttempt.Phase.PERSISTENCE),
                result.attempts().stream().map(AwaitAttempt::phase).toList());
    }

    @Test
    void successfulSequenceReportsAllCapturedStages() {
        var sequence = captured(Conditions.<String>matches(value -> true), matches(value -> true));
        var evaluator = sequence.newSession();
        evaluator.apply("first");
        var success = assertInstanceOf(ConditionResult.Satisfied.class, evaluator.apply("last"));
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class, success.context());
        assertEquals(2, context.capturedStages());
        assertEquals(2, context.evaluatedStageNumber());
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
                            var evaluation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class,
                                    result.attempts().getLast().outcome()).evaluation();
                            var context = assertInstanceOf(ConditionResult.Context.Sequence.class, evaluation.context());
                            assertEquals(stage - 1, context.capturedStages());
                            assertEquals(stage, context.evaluatedStageNumber());
                        } else {
                            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(first, second)).failure();
                        }
                        assertEquals(INTERRUPTED, failure.reason());
                        assertInstanceOf(InterruptedException.class, failure.cause());
                        assertTrue(failure.message().contains("Sequence (captured " + (stage - 1) + " of 2)"));
                        assertTrue(failure.message().contains("Importance: " + (stage == 1 ? "first" : "second") + " stage"));
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
                    () -> timedAwait(() -> "ready", new WaitConfiguration(1, 5, 0), time, time).until(sequence));
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
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getFirst().outcome()).evaluation());

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
    void capturesConditionResults() {
        var time = new FakeTime(0);
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(condition("length 1", value -> value.length() == 1
                        ? satisfied(value.length()) : unsatisfied("length was not 1")),
                condition("length 2", value -> value.length() == 2
                        ? satisfied(value.length()) : unsatisfied("length was not 2")));

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
    void nestedSequenceFailureRetainsTheInnerStageContext() {
        var time = new FakeTime(0);
        Condition<String, List<String>> inner = captured(
                Conditions.<String, String>condition("inner first", value -> satisfied(value)),
                Conditions.<String, String>condition("inner second", value -> unsatisfied("inner mismatch"))
                        .because("inner business reason"));

        var result = timedAwait(() -> "actual", config(1, 3, 0), time, time).tryUntil(Conditions.<String, List<String>>condition("outer first", value -> satisfied(List.of(value))), inner);
        var outcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getLast().outcome()).evaluation());
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class,
                outcome.context());

        assertEquals("inner mismatch", outcome.mismatch());
        assertEquals("inner second", context.expectation());
        assertEquals("inner business reason", context.importance());
    }

    @Test
    void validatesEveryStageBeforeEvaluation() {
        PreservingCondition<String> preserving = matches(value -> true);
        Condition<String, Integer> transforming = condition("length",
                value -> satisfied(value.length()));

        assertThrows(NullPointerException.class,
                () -> captured((PreservingCondition<String>) null, preserving));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, preserving, (PreservingCondition<String>[]) null));
        assertThrows(NullPointerException.class,
                () -> captured((Condition<String, Integer>) null, transforming));
        assertThrows(NullPointerException.class,
                () -> captured(transforming, transforming,
                        (Condition<String, Integer>) null));
    }

    @Test
    void returnsAnImmutableCapturedCopyThatAllowsNull() {
        var time = new FakeTime(0);

        List<String> result = timedAwait(() -> "observed", config(1, 10, 0),
                time, time).until(condition("first null", value -> satisfied((String) null)),
                condition("second null", value -> satisfied((String) null)));

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    void capturesTheVarargsTailDefensively() {
        var time = new FakeTime(0);
        PreservingCondition<Integer> matchesOne = matches(value -> value == 1);
        @SuppressWarnings("unchecked")
        PreservingCondition<Integer>[] rest = (PreservingCondition<Integer>[]) new PreservingCondition<?>[]{matchesOne};
        Condition<Integer, List<Integer>> sequence = captured(
                matchesOne, matchesOne, rest);
        rest[0] = matches(value -> false);

        List<Integer> result = timedAwait(() -> 1, config(1, 10, 0),
                time, time).until(sequence);

        assertEquals(List.of(1, 1, 1), result);
    }

    @Test
    void selectedSequenceCapturesSelectedValues() {
        var time = new FakeTime(0);
        List<Optional<String>> observations = List.of(Optional.of("first"),
                Optional.of("second"));
        int[] next = {0};

        List<String> result = timedOptionalAwait(() -> observations.get(next[0]++),
                config(1, 10, 0), time, time).until(present, present);

        assertEquals(List.of("first", "second"), result);
    }

    @Test
    void reusesOneSequenceDefinitionAcrossExecutions() {
        Condition<Payment, List<Payment>> lifecycle = captured(matches(value -> value.status() == Status.CREATED),
                matches(value -> value.status() == Status.FINISHED));
        var created = new Payment(Status.CREATED, "accepted");
        var firstFinished = new Payment(Status.FINISHED, "first");
        var secondFinished = new Payment(Status.FINISHED, "second");

        assertEquals(List.of(created, firstFinished), runLifecycle(
                new Payment[]{created, firstFinished}, lifecycle, 10));
        assertEquals(List.of(created, secondFinished), runLifecycle(
                new Payment[]{created, secondFinished}, lifecycle, 10));
    }

    @Test
    void timeoutDoesNotAdvanceTheNextExecution() {
        Condition<Payment, List<Payment>> lifecycle = captured(matches(value -> value.status() == Status.CREATED),
                matches(value -> value.status() == Status.FINISHED));
        var created = new Payment(Status.CREATED, "accepted");
        var pending = new Payment(Status.PENDING, "waiting");
        var finished = new Payment(Status.FINISHED, "paid");

        assertFailure(TIMEOUT, () -> runLifecycle(
                new Payment[]{created, pending, pending}, lifecycle, 3));
        assertEquals(List.of(created, finished), runLifecycle(
                new Payment[]{created, finished}, lifecycle, 10));
    }

    private static List<Payment> runLifecycle(Payment[] observations,
            Condition<Payment, List<Payment>> lifecycle, long timeout) {
        var time = new FakeTime(0);
        int[] next = {0};
        return timedAwait(() -> observations[Math.min(next[0]++, observations.length - 1)],
                config(1, timeout, 0), time, time).until(lifecycle);
    }
}
