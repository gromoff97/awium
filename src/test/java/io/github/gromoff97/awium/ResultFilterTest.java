package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.first;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class ResultFilterTest {

    private record User(int id, boolean active) {
        boolean checkActive() throws IOException { return active; }
    }

    private static final User PENDING = new User(0, false);
    private static final User FIRST = new User(1, true);
    private static final User LAST = new User(2, true);

    @Test
    void preservingChecksFilterTheSameObservationAndKeepItsConcreteType() {
        var ready = new StringBuilder("ready");
        var observations = List.of(new StringBuilder("failed"), new StringBuilder("really"), ready).iterator();
        int[] filters = {0};
        var condition = StringConditions.startsWith("rea").where(text -> {
            filters[0]++;
            return text.length() == 5;
        }).because("ready text");
        var time = new FakeTime(0);
        StringBuilder selected = await(observations::next).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(3)).until(condition);
        assertSame(ready, selected);
        assertEquals(ready.capacity(), selected.capacity());
        assertEquals(2, filters[0]);
        assertFalse(observations.hasNext());

        AwaitResult<StringBuilder, StringBuilder> result = await(() -> ready).usingTime(time, time).tryUntil(condition);
        assertSame(ready, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        List<StringBuilder> stages = await(() -> ready).usingTime(time, time).every(ofNanos(1)).until(condition, condition);
        assertSame(ready, stages.getFirst());
        assertEquals(ready.capacity(), stages.getLast().capacity());

        var firstUser = matches(User::active).where(user -> user.id() == 1).because("first active user");
        User user = await(() -> List.of(PENDING, LAST, FIRST)).usingTime(time, time).until(single(firstUser));
        assertSame(FIRST, user);
        assertEquals(1, user.id());
    }

    @Test
    void narrowedResultsBindUntypedLambdasAndCaptureOrderedValues() {
        var observations = new ArrayDeque<>(List.of("skip", PENDING, FIRST, LAST));
        var time = new FakeTime(0);
        var waiting = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5));

        var users = waiting.until(
                instanceOf(User.class).where(user -> user.id() == 1).because("first user"),
                instanceOf(User.class).where(user -> user.id() == 2).because("last user"));
        assertSame(FIRST, users.getFirst());
        assertEquals(2, users.getLast().id());
        assertTrue(observations.isEmpty());
        Object observed = FIRST;
        assertEquals(1, await(() -> observed).usingTime(time, time)
                .until(instanceOf(User.class).where(User::checkActive).because("active user")).id());
    }

    @Test
    void filteringBeforeAndAfterASelectionHasDistinctMeaning() {
        List<Object> values = List.of("skip", PENDING, FIRST);
        var time = new FakeTime(0);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var active = instanceOf(User.class).where(user -> user.active()).because("active user");

        assertEquals(1, waiting.until(single(active)).id());
        assertEquals(1, waiting.until(first(active)).id());
        AwaitResult<List<Object>, User> captured = waiting.tryUntil(single(active));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var user) -> assertSame(FIRST, user);
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
        int[] checks = {0};
        var result = waiting.tryUntil(single(instanceOf(User.class)).where(user -> { checks[0]++; return user.active(); }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("more than one value matched"));
        assertEquals(0, checks[0]);
    }

    @Test
    void failedPrerequisitesKeepTheirMismatchAndExtractionReference() {
        var time = new FakeTime(0);
        int[] checks = {0};
        var narrowed = MapConditions.valueFor("user", instanceOf(User.class))
                .where(user -> { checks[0]++; return user.active(); }).because("required user");
        Map<String, Object> absent = Map.of();
        var result = await(() -> absent).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(narrowed);
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertEquals(0, checks[0]);
        assertTrue(failure.message().contains("Because: required user"));
        var attempt = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, result.attempts().getLast().outcome());
        assertEquals("user", attempt.condition().reference().value());
    }

    @Test
    void explanationsStayLazyAndAnOuterExplanationReplacesTheInheritedOne() {
        int[] outer = {0};
        Supplier<String> forbidden = () -> { throw new AssertionError("unused explanation rendered"); };
        var time = new FakeTime(0);
        Object observed = FIRST;
        var original = instanceOf(User.class).because(forbidden);
        assertEquals(1, await(() -> observed).usingTime(time, time).until(original.where(User::active)).id());
        var result = await(() -> observed).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).tryUntil(
                original.where(user -> false).because(() -> { outer[0]++; return "active required"; }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("value did not match"));
        assertTrue(failure.message().contains("Because: active required"));
        assertTrue(failure.suppressed().isEmpty());
        assertEquals(1, outer[0]);
    }

    @Test
    void predicateAndPrerequisiteFailuresRetainAnyOriginalThrowable() {
        for (Throwable cause : List.of(new IOException("checked"), new AssertionError("assertion"),
                new Throwable("throwable"), new InterruptedException("ordinary failure"))) {
            int[] checks = {0};
            var time = new FakeTime(0);
            Object observed = FIRST;
            var result = await(() -> observed).usingTime(time, time).tryUntil(instanceOf(User.class)
                    .where(user -> { checks[0]++; throw cause; }).because("ready"));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
            assertSame(cause, failure.cause());
            assertEquals(1, checks[0]);
            assertEquals(1, result.attempts().size());
            assertFalse(Thread.currentThread().isInterrupted());

            checks[0] = 0;
            Map<String, Object> broken = new AbstractMap<>() {
                @Override public Set<Map.Entry<String, Object>> entrySet() {
                    return Set.of(new SimpleEntry<>("user", observed) {
                        @Override public Object getValue() { return throwUnchecked(cause); }
                    });
                }
                @Override public String toString() { return "broken map"; }
            };
            var prior = await(() -> broken).usingTime(time, time).tryUntil(MapConditions.valueFor("user", instanceOf(User.class))
                    .where(user -> { checks[0]++; return true; }));
            var priorFailure = assertInstanceOf(AwaitResult.Failed.class, prior).failure();
            assertSame(cause, priorFailure.cause());
            assertEquals(0, checks[0]);
            var attempt = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, prior.attempts().getFirst().outcome());
            assertEquals("user", attempt.condition().reference().value());
        }
    }

    @Test
    void interruptionIsCheckedBeforeTheNextCallbackAndAfterTheFilter() {
        var time = new FakeTime(0);
        Object observed = FIRST;
        for (boolean matches : List.of(false, true)) {
            try {
                var result = await(() -> observed).usingTime(time, time).tryUntil(instanceOf(User.class).where(user -> {
                    Thread.currentThread().interrupt();
                    return matches;
                }));
                assertEquals(AwaitFailure.Reason.INTERRUPTED, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
                assertEquals(1, result.attempts().size());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
        int[] checks = {0};
        Map<String, Object> interrupting = new AbstractMap<>() {
            @Override public Set<Map.Entry<String, Object>> entrySet() {
                return Set.of(new SimpleEntry<>("user", observed) {
                    @Override public Object getValue() { Thread.currentThread().interrupt(); return super.getValue(); }
                });
            }
            @Override public String toString() { return "interrupting map"; }
        };
        try {
            var result = await(() -> interrupting).usingTime(time, time).tryUntil(MapConditions.valueFor("user", instanceOf(User.class))
                    .where(user -> { checks[0]++; return true; }));
            assertEquals(AwaitFailure.Reason.INTERRUPTED, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
            assertEquals(0, checks[0]);
        } finally {
            Thread.interrupted();
        }
    }
}
