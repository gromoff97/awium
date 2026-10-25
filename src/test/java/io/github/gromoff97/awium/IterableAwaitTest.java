package io.github.gromoff97.awium;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class IterableAwaitTest {

    private static final User ACTIVE = new User(1, true);
    private static final User INACTIVE = new User(2, false);

    @Test
    void mapsThatAlsoIterateValuesKeepMapSelectionTypes() {
        var both = new MapWithUsers();
        var waiting = await(() -> both);

        assertEquals(10, waiting.until(MapConditions.singleEntry).getValue().intValue());
        assertEquals(10, waiting.until(MapConditions.valueFor("key")).intValue());
        assertSame(both, waiting.until(all(User::active).because("iterable users")));
        assertTrue(waiting.until(single(instanceOf(User.class)).because("one user")).active());
        AwaitResult<MapWithUsers, Map.Entry<String, Integer>> captured = waiting.tryUntil(MapConditions.singleEntry);
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var entry) -> assertEquals(10, entry.getValue().intValue());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
    }

    @Test
    void typedSelectionsAndQuantifiersKeepConcreteTypesAcrossSourceForms() {
        var users = new Users(List.of(ACTIVE));
        var time = new FakeTime(0);
        var waiting = await(() -> users).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertTrue(waiting.until(single(instanceOf(User.class)).because("user")).active());
        assertEquals(1, waiting.until(all(User::active).because("active")).count());
        assertEquals(1, waiting.until(any(User::active).because("active")).count());
        assertEquals(1, waiting.until(none(User::inactive).because("active")).count());
        AwaitResult<Users, User> captured = waiting.tryUntil(single(instanceOf(User.class)).because(() -> "one user"));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var value) -> assertTrue(value.active());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }

        Callable<Iterable<User>> jdk = () -> users;
        assertTrue(await(jdk).until(single(instanceOf(User.class)).because("JDK callable")).active());
        Iterable<? extends User> view = users;
        assertTrue(await(() -> view).until(single(instanceOf(User.class)).because("view")).active());
    }

    @Test
    void orderedCapturesAndPersistenceKeepTheirElementTypes() {
        int[] reads = {0};
        var time = new FakeTime(0);
        var values = await(() -> new Users(List.of(reads[0]++ == 0 ? INACTIVE : ACTIVE)))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).persisting(ofNanos(1))
                .until(single(instanceOf(User.class)).because("initial"), single(instanceOf(User.class)).because("next"));

        assertEquals(2, values.size());
        assertFalse(values.getFirst().active());
        assertTrue(values.getLast().active());
        assertTrue(reads[0] >= 3);
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void selectionsAndQuantifiersTraverseEachObservationOnlyOnce() {
        List<Function<Once, AwaitResult<?, ?>>> checks = List.of(
                values -> await(() -> values).tryUntil(single(instanceOf(User.class)).because("unique user")),
                values -> await(() -> values).tryUntil(all(User::active)),
                values -> await(() -> values).tryUntil(any(User::active)),
                values -> await(() -> values).tryUntil(none(User::inactive)));
        for (var check : checks) {
            var values = new Once();
            assertInstanceOf(AwaitResult.Satisfied.class, check.apply(values));
            assertEquals(1, values.iterations);
        }
    }

    @Test
    void mismatchesAndIteratorFailuresKeepTheirOutcomes() {
        for (Iterable<User> values : List.of(new Users(List.of()), new Users(List.of(ACTIVE, INACTIVE)))) {
            var time = new FakeTime(0);
            var result = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                    .tryUntil(single(instanceOf(User.class)).because("one user"));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Because: one user"));
        }

        var cause = new IllegalStateException("iterator failed");
        Iterable<User> broken = () -> { throw cause; };
        var failure = assertInstanceOf(AwaitResult.Failed.class, await(() -> broken).tryUntil(single(instanceOf(User.class)))).failure();
        assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
        assertSame(cause, failure.cause());
    }

    @Test
    void transformedNestedSelectionsAcceptIterableValues() {
        var users = new Users(List.of(ACTIVE));

        assertTrue(await(() -> Optional.of(users)).until(OptionalConditions.hasValue(
                single(instanceOf(User.class)).because("iterable"))).active());
        assertTrue(await(() -> Map.of("users", users)).until(MapConditions.valueFor("users",
                single(instanceOf(User.class)).because("iterable"))).active());
        assertTrue(await(() -> List.of(users)).until(single(
                single(instanceOf(User.class)).because("iterable"))).active());
    }

    private record User(int id, boolean active) {
        boolean inactive() { return !active; }
    }

    private record Users(List<User> values) implements Iterable<User> {
        @Override public Iterator<User> iterator() { return values.iterator(); }
        int count() { return values.size(); }
    }

    private static final class MapWithUsers extends AbstractMap<String, Integer> implements Iterable<User> {
        @Override public Set<Map.Entry<String, Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
        @Override public Iterator<User> iterator() { return List.of(ACTIVE).iterator(); }
    }

    private static final class Once implements Iterable<User> {
        private int iterations;

        @Override
        public Iterator<User> iterator() {
            if (++iterations != 1) throw new AssertionError("observation was traversed again");
            return List.of(ACTIVE).iterator();
        }
    }
}
