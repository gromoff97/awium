package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertUnsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.observation;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.Objects.requireNonNullElse;
import static org.junit.jupiter.api.Assertions.*;

class MixedConditionSequenceTest {

    private record User(int stage, boolean ready) {
        boolean pending() { return stage == 2; }
    }

    private static final User CREATED = new User(1, false);
    private static final User PENDING = new User(2, false);
    private static final User READY = new User(3, true);

    @Test
    void nestedComparisonsAndMethodReferencesCaptureOriginalContainersWithoutTypeHints() {
        var optionalValues = List.of(Optional.of(CREATED), Optional.of(PENDING), Optional.of(READY));
        var optionalSource = optionalValues.iterator();
        var optional = await(optionalSource::next).every(ofNanos(1)).until(captured(
                hasValue(equalTo(CREATED)), hasValue(User::ready)));
        assertSame(optionalValues.getFirst(), optional.getFirst());
        assertSame(optionalValues.getLast(), optional.getLast());
        assertEquals(3, optional.getLast().orElseThrow().stage());
        assertFalse(optionalSource.hasNext());

        var collections = List.of(List.of(CREATED), List.of(PENDING), List.of(READY));
        var collectionSource = collections.iterator();
        var collection = tryAwait(collectionSource::next).every(ofNanos(1)).until(captured(
                singleElement(equalTo(CREATED)), singleElement(User::ready)));
        assertSame(collections.getFirst(), collection.orThrow().getFirst());
        assertSame(collections.getLast(), collection.orThrow().getLast());
        assertEquals(3, collection.orThrow().getLast().getFirst().stage());
        assertEquals(3, attempts(collection).size());
        assertSame(collections.get(1), observation(attempts(collection).get(1)));
        assertInstanceOf(UnsatisfiedObservation.class, attempts(collection).get(1));
        assertFalse(collectionSource.hasNext());

        var maps = List.of(Map.of("user", CREATED), Map.of("user", PENDING), Map.of("user", READY));
        var mapSource = maps.iterator();
        var map = tryAwait(mapSource::next).every(ofNanos(1)).until(captured(
                valueFor("user", equalTo(CREATED)).because("created"),
                valueFor("user", User::ready).because("ready")));
        assertSame(maps.getFirst(), map.orThrow().getFirst());
        assertSame(maps.getLast(), map.orThrow().getLast());
        assertEquals(3, map.orThrow().getLast().get("user").stage());
        assertEquals(3, attempts(map).size());
        var retry = attempts(map).get(1);
        assertSame(maps.get(1), observation(retry));
        assertEquals("ready", map.conditions().getLast().condition().explanation());
        assertEquals(2L, map.conditions().getLast().attempts().firstKey());
        assertInstanceOf(UnsatisfiedObservation.class, retry);
        assertFalse(mapSource.hasNext());
    }

    @Test
    void alternatingChecksCaptureDistinctObservationsAndKeepInferredAndExplicitTypes() {
        var observations = List.of(CREATED, CREATED, PENDING, READY, READY);
        var source = observations.iterator();
        var users = await(source::next).every(ofNanos(1)).until(captured(
                equalTo(CREATED), matches(User::pending),
                equalTo(READY), matches(User::ready)));
        assertEquals(List.of(CREATED, PENDING, READY, READY), users);
        assertSame(CREATED, users.getFirst());
        assertEquals(3, users.getLast().stage());
        assertFalse(source.hasNext());

        var nextSource = observations.iterator();
        AwaitResult<User, List<User>> captured = tryAwait(nextSource::next)
                .every(ofNanos(1)).until(captured(
                        equalTo(CREATED), matches(User::pending),
                        equalTo(READY), matches(User::ready)));
        switch (captured) {
            case Succeeded(var history, var values) -> {
                assertEquals(users, values);
                assertEquals(3, values.getLast().stage());
                assertSame(READY, values.getLast());
            }
            case Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(5, attempts(captured).size());
        assertFalse(nextSource.hasNext());
    }

    @Test
    void repeatedFactoryBackedStagesStayIndependentAcrossMixedWaits() {
        int[] factories = {0};
        Condition<User, User> afterRetry = conditionPerWait("second invocation", () -> {
            factories[0]++;
            var outcomes = new ArrayDeque<>(List.<ConditionResult<User>>of(unsatisfied("retry"), satisfied(CREATED)));
            return user -> requireNonNullElse(outcomes.pollFirst(), unsatisfied("retry"));
        });
        var values = List.of(CREATED, CREATED, PENDING, CREATED, CREATED);
        var source = values.iterator();
        List<User> users = await(source::next).every(ofNanos(1)).until(captured(
                afterRetry, equalTo(PENDING), afterRetry));
        assertEquals(List.of(CREATED, PENDING, CREATED), users);
        assertEquals(2, factories[0]);
        assertFalse(source.hasNext());

        var nextSource = values.iterator();
        AwaitResult<User, List<User>> result = tryAwait(nextSource::next)
                .every(ofNanos(1)).until(captured(afterRetry, equalTo(PENDING), afterRetry));
        assertEquals(users, result.orThrow());
        assertEquals(4, factories[0]);
        assertFalse(nextSource.hasNext());
    }

    @Test
    void comparisonStageFollowsThePredicateAndKeepsItsOperand() {
        var last = equalTo(READY);
        var run = ConditionExecution.<User, List<User>>start(captured(matches((User user) -> user.stage() == 1), last), false);
        probe(run, CREATED);
        assertUnsatisfied(probe(run, CREATED));
        assertEquals(List.of(CREATED), run.result());
        assertSame(READY, last.references().getFirst().value());
        probe(run, READY);
        assertEquals(List.of(CREATED, READY), run.result());
    }

    @Test
    void predicateStageFollowsTheComparison() {
        var run = ConditionExecution.<User, List<User>>start(captured(equalTo(CREATED), matches(User::ready)), false);
        probe(run, CREATED);
        assertUnsatisfied(probe(run, CREATED));
        assertEquals(List.of(CREATED), run.result());
        probe(run, READY);
        assertEquals(List.of(CREATED, READY), run.result());
    }

    @Test
    void failedMixedStageRetainsItsOriginalCause() {
        var cause = new IOException("callback failed");
        var failed = tryAwait(() -> CREATED).every(ofNanos(1)).until(captured(
                equalTo(CREATED), matches((User user) -> { throw cause; }), equalTo(READY)));
        var failure = failure(CONDITION_FAILED, failed);
        assertSame(cause, failure.cause());
        assertEquals(2, attempts(failed).size());
    }

    @Test
    void recheckingTheFinalStageCapturesItsLatestValue() {
        var acquired = new User(3, true);
        var latest = new User(3, true);
        var run = ConditionExecution.<User, List<User>>start(captured(equalTo(CREATED), matches(User::pending), equalTo(READY)), false);
        for (User user : List.of(CREATED, PENDING, acquired, latest)) probe(run, user);
        assertSame(CREATED, run.result().getFirst());
        assertSame(PENDING, run.result().get(1));
        assertSame(latest, run.result().getLast());
    }
}
