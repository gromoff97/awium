package io.github.gromoff97.awium;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.fluent.Condition;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedOptionalAwait;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.fluent.Conditions.captured;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static io.github.gromoff97.awium.fluent.Conditions.matches;
import static io.github.gromoff97.awium.fluent.OptionalConditions.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapturedConditionTest {

    private record Payment(Status status, String detail) {}

    private enum Status { CREATED, PENDING, FINISHED }

    @Test
    void capturesExactlyOnePredicateStagePerPollingAttempt() {
        var time = new FakeTime(0);
        int[] calls = {0};

        List<Integer> result = timedAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, 10, 0), time, time).until(captured(
                value -> value == 1,
                value -> value == 1,
                value -> value == 1));

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
        }, config(1, 2, 0), time, time).until(captured(value -> true, value -> true));
        var outcome = assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class,
                result.attempts().getFirst().outcome());

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
                config(1, 10, 0), time, time).until(captured(
                payment -> payment.status() == Status.CREATED,
                payment -> payment.status() == Status.PENDING,
                payment -> payment.status() == Status.FINISHED));

        assertEquals(List.of(created, pending, finished), result);
    }

    @Test
    void persistsByEvaluatingOnlyTheFinalCapturedStage() {
        var time = new FakeTime(0);
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String persistedFinal = new String("persisted final");
        String[] observations = {early, acquiredFinal, persistedFinal, persistedFinal};
        int[] sourceCalls = {0};
        int[] earlyStageCalls = {0};
        int[] finalStageCalls = {0};

        List<String> result = timedAwait(() -> observations[sourceCalls[0]++],
                config(1, 10, 2), time, time).until(captured(
                value -> {
                    earlyStageCalls[0]++;
                    return value == early;
                },
                value -> {
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
    void persistenceReplacesOnlyTheFinalCapturedResult() {
        var time = new FakeTime(0);
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String firstPersistedFinal = new String("first persisted final");
        String finalPersistedFinal = new String("final persisted final");
        String[] observations = {early, acquiredFinal, firstPersistedFinal,
                finalPersistedFinal};
        int[] sourceCalls = {0};

        List<String> result = timedAwait(() -> observations[sourceCalls[0]++],
                config(1, 10, 2), time, time).until(captured(
                value -> value == early,
                value -> value != early));

        assertSame(early, result.get(0));
        assertSame(finalPersistedFinal, result.get(1));
        assertEquals(4, sourceCalls[0]);
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

        assertThrows(AwaitPersistenceException.class, () -> timedAwait(
                () -> observations[sourceCalls[0]++], config(1, 10, 10), time, time).until(captured(value -> value == early,
                        value -> value != rejectedFinal)));

        assertEquals(3, sourceCalls[0]);
        assertEquals(List.of(1L, 1L), time.parkRequests);
    }

    @Test
    void doesNotSkipAMissedIntermediateStage() {
        var time = new FakeTime(0);
        var created = new Payment(Status.CREATED, "accepted");
        var finished = new Payment(Status.FINISHED, "paid");
        int[] calls = {0};

        assertThrows(AwaitTimeoutException.class, () -> timedAwait(() ->
                calls[0]++ == 0 ? created : finished,
                config(1, 3, 0), time, time).until(captured(
                payment -> payment.status() == Status.CREATED,
                payment -> payment.status() == Status.PENDING,
                payment -> payment.status() == Status.FINISHED)));

        assertEquals(3, calls[0]);
    }

    @Test
    void capturesConditionResults() {
        var time = new FakeTime(0);
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(
                condition("length 1", value -> value.length() == 1
                        ? satisfied(value.length()) : unsatisfied("length was not 1")),
                condition("length 2", value -> value.length() == 2
                        ? satisfied(value.length()) : unsatisfied("length was not 2"))));

        assertEquals(List.of(1, 2), result);
    }

    @Test
    void preservingSequenceMixesPlainAndExplainedStages() {
        var time = new FakeTime(0);
        String[] observations = {"alpha", "omega"};
        int[] next = {0};

        List<String> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(
                matches((String value) -> value.startsWith("a")),
                matches((String value) -> value.endsWith("a")).because("final state")));

        assertEquals(List.of("alpha", "omega"), result);
    }

    @Test
    void validatesEveryStageBeforeEvaluation() {
        Predicate<String> predicate = value -> true;
        PreservingStage<String> preserving = matches(predicate);
        ResultStage<String, Integer> transforming = condition("length",
                value -> satisfied(value.length()));

        assertThrows(NullPointerException.class,
                () -> captured((Predicate<String>) null, predicate));
        assertThrows(NullPointerException.class,
                () -> captured(predicate, (Predicate<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(predicate, predicate, (Predicate<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(predicate, predicate, (Predicate<String>[]) null));
        assertThrows(NullPointerException.class,
                () -> captured((PreservingStage<String>) null, preserving));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, preserving, (PreservingStage<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured((ResultStage<String, Integer>) null, transforming));
        assertThrows(NullPointerException.class,
                () -> captured(transforming, transforming,
                        (ResultStage<String, Integer>) null));
    }

    @Test
    void returnsAnImmutableCapturedCopyThatAllowsNull() {
        var time = new FakeTime(0);

        List<String> result = timedAwait(() -> "observed", config(1, 10, 0),
                time, time).until(captured(
                condition("first null", value -> satisfied((String) null)),
                condition("second null", value -> satisfied((String) null))));

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    void capturesTheVarargsTailDefensively() {
        var time = new FakeTime(0);
        Predicate<Integer> matchesOne = value -> value == 1;
        @SuppressWarnings("unchecked")
        Predicate<Integer>[] rest = (Predicate<Integer>[]) new Predicate<?>[]{matchesOne};
        Condition<Integer, List<Integer>> sequence = captured(
                matchesOne, matchesOne, rest);
        rest[0] = value -> false;

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
                config(1, 10, 0), time, time).until(captured(present, present));

        assertEquals(List.of("first", "second"), result);
    }

    @Test
    void reusesOneSequenceDefinitionAcrossExecutions() {
        var lifecycle = captured(
                (Payment value) -> value.status() == Status.CREATED,
                value -> value.status() == Status.FINISHED);
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
        var lifecycle = captured(
                (Payment value) -> value.status() == Status.CREATED,
                value -> value.status() == Status.FINISHED);
        var created = new Payment(Status.CREATED, "accepted");
        var pending = new Payment(Status.PENDING, "waiting");
        var finished = new Payment(Status.FINISHED, "paid");

        assertThrows(AwaitTimeoutException.class, () -> runLifecycle(
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

    private static WaitConfiguration config(long every, long upTo,
            long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }
}
