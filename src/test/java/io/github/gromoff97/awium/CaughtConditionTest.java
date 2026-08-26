package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;
import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.matches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaughtConditionTest {

    private record Payment(Status status, String detail) {}

    private enum Status { CREATED, PENDING, FINISHED }

    @Test
    void capturesExactlyOnePredicateStagePerPollingAttempt() {
        var time = new FakeTime(0);
        int[] calls = {0};

        List<Integer> result = timedAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, 10, 0), time, time).until(caught(
                value -> value == 1,
                value -> value == 1,
                value -> value == 1));

        assertEquals(List.of(1, 1, 1), result);
        assertEquals(3, calls[0]);
        assertEquals(List.of(1L, 1L), time.parkRequests);
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
                config(1, 10, 0), time, time).until(caught(
                payment -> payment.status() == Status.CREATED,
                payment -> payment.status() == Status.PENDING,
                payment -> payment.status() == Status.FINISHED));

        assertEquals(List.of(created, pending, finished), result);
    }

    @Test
    void doesNotSkipAMissedIntermediateStage() {
        var time = new FakeTime(0);
        var created = new Payment(Status.CREATED, "accepted");
        var finished = new Payment(Status.FINISHED, "paid");
        int[] calls = {0};

        assertThrows(AwaitTimeoutException.class, () -> timedAwait(() ->
                calls[0]++ == 0 ? created : finished,
                config(1, 3, 0), time, time).until(caught(
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
                config(1, 10, 0), time, time).until(caught(
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
                config(1, 10, 0), time, time).until(caught(
                matches((String value) -> value.startsWith("a")),
                matches((String value) -> value.endsWith("a")).because("final state")));

        assertEquals(List.of("alpha", "omega"), result);
    }

    @Test
    void validatesEveryStageBeforeEvaluation() {
        Predicate<String> predicate = value -> true;
        PreservingStage<String> preserving = matches(predicate);
        ConditionStage<String, Integer> transforming = condition("length",
                value -> satisfied(value.length()));

        assertThrows(NullPointerException.class,
                () -> caught((Predicate<String>) null, predicate));
        assertThrows(NullPointerException.class,
                () -> caught(predicate, (Predicate<String>) null));
        assertThrows(NullPointerException.class,
                () -> caught(predicate, predicate, (Predicate<String>) null));
        assertThrows(NullPointerException.class,
                () -> caught(predicate, predicate, (Predicate<String>[]) null));
        assertThrows(NullPointerException.class,
                () -> caught((PreservingStage<String>) null, preserving));
        assertThrows(NullPointerException.class,
                () -> caught(preserving, preserving, (PreservingStage<String>) null));
        assertThrows(NullPointerException.class,
                () -> caught((ConditionStage<String, Integer>) null, transforming));
        assertThrows(NullPointerException.class,
                () -> caught(transforming, transforming,
                        (ConditionStage<String, Integer>) null));
    }

    @Test
    void returnsAnImmutableCapturedCopyThatAllowsNull() {
        var time = new FakeTime(0);

        List<String> result = timedAwait(() -> "observed", config(1, 10, 0),
                time, time).until(caught(
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
        Condition<Integer, List<Integer>> sequence = caught(
                matchesOne, matchesOne, rest);
        rest[0] = value -> false;

        List<Integer> result = timedAwait(() -> 1, config(1, 10, 0),
                time, time).until(sequence);

        assertEquals(List.of(1, 1, 1), result);
    }

    private static WaitConfiguration config(long every, long upTo,
            long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }
}
