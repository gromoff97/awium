package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MixedConditionSequenceTest {

    private record User(int stage, boolean ready) {
        boolean pending() { return stage == 2; }
    }

    private static final User CREATED = new User(1, false);
    private static final User PENDING = new User(2, false);
    private static final User READY = new User(3, true);

    @Test
    void alternatingChecksCaptureDistinctObservationsAndKeepInferredAndExplicitTypes() {
        var observations = List.of(CREATED, CREATED, PENDING, READY, READY);
        var source = observations.iterator();
        var time = new FakeTime(0);
        var users = await(source::next).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10)).until(
                equalTo(CREATED).because("created"), matches(User::pending).because("pending"),
                equalTo(READY).because("ready"), matches(User::ready).because("confirmed"));
        assertEquals(List.of(CREATED, PENDING, READY, READY), users);
        assertSame(CREATED, users.getFirst());
        assertEquals(3, users.getLast().stage());
        assertFalse(source.hasNext());

        var nextSource = observations.iterator();
        AwaitResult<User, List<User>> captured = await(nextSource::next).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).tryUntil(
                        equalTo(CREATED).because(() -> "created"), matches(User::pending).because("%s", "pending"),
                        equalTo(READY).because("ready"), matches(User::ready).because("confirmed"));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var values) -> {
                assertEquals(users, values);
                assertEquals(3, values.getLast().stage());
                assertSame(READY, values.getLast());
            }
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
        assertEquals(5, captured.totalAttempts());
        assertFalse(nextSource.hasNext());
    }

    @Test
    void repeatedFactoryBackedStagesStayIndependentAcrossMixedWaits() {
        int[] factories = {0};
        PreservingCondition<User> afterRetry = conditionPerWait("second invocation", () -> {
            factories[0]++;
            int[] checks = {0};
            return user -> ++checks[0] == 2 ? satisfied() : unsatisfied("retry");
        });
        var time = new FakeTime(0);
        var values = List.of(CREATED, CREATED, PENDING, CREATED, CREATED);
        var source = values.iterator();
        List<User> users = await(source::next).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10)).until(
                afterRetry.because("first retry"), equalTo(PENDING), afterRetry.because("last retry"));
        assertEquals(List.of(CREATED, PENDING, CREATED), users);
        assertEquals(2, factories[0]);
        assertFalse(source.hasNext());

        var nextSource = values.iterator();
        AwaitResult<User, List<User>> result = await(nextSource::next).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).tryUntil(afterRetry, equalTo(PENDING), afterRetry);
        assertEquals(users, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(4, factories[0]);
        assertFalse(nextSource.hasNext());
    }

    @Test
    void diagnosticsRetainTheCurrentMixedStageAndOriginalCause() {
        var time = new FakeTime(0);
        for (boolean comparisonLast : List.of(false, true)) {
            AwaitResult<User, List<User>> result = comparisonLast
                    ? await(() -> CREATED).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                            .tryUntil(matches(user -> user.stage() == 1), equalTo(READY).because("final state"))
                    : await(() -> CREATED).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                            .tryUntil(equalTo(CREATED), matches(User::ready).because("final state"));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Because: final state"));
            var attempt = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, result.attempts().getLast().outcome());
            assertEquals(1, attempt.progress().capturedStages());
            assertEquals(2, attempt.progress().evaluatedStageNumber());
            if (comparisonLast) assertSame(READY, attempt.progress().condition().reference().value());
        }

        var cause = new IOException("callback failed");
        var failed = await(() -> CREATED).usingTime(time, time).every(ofNanos(1)).tryUntil(
                equalTo(CREATED), matches((User user) -> { throw cause; }).because("failed stage"), equalTo(READY));
        var failure = assertInstanceOf(AwaitResult.Failed.class, failed).failure();
        assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
        assertSame(cause, failure.cause());
        assertEquals(2, failed.totalAttempts());
    }

    @Test
    void persistenceRechecksOnlyTheFinalStageAndCapturesItsLatestValue() {
        var acquired = new User(3, true);
        var middle = new User(3, true);
        var latest = new User(3, true);
        var source = List.of(CREATED, PENDING, acquired, middle, latest).iterator();
        var time = new FakeTime(0);
        List<User> users = await(source::next).usingTime(time, time).every(ofNanos(1))
                .upTo(ofNanos(3)).persisting(ofNanos(2)).until(
                        equalTo(CREATED), matches(User::pending), equalTo(READY));
        assertSame(CREATED, users.getFirst());
        assertSame(PENDING, users.get(1));
        assertSame(latest, users.getLast());
        assertFalse(source.hasNext());
    }
}
