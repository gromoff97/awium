package io.github.gromoff97.awium;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class TypedCollectionSelectionTest {

    private record User(int id) {
        boolean positive() { return id > 0; }
    }
    private static final User FIRST = new User(1);
    private static final User LAST = new User(2);

    @Test
    void firstLastAndIndexedConditionsReturnTheirDeclaredValues() {
        List<Object> values = List.of("skip", FIRST, 99, LAST);
        var time = new FakeTime(0);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        Supplier<String> forbidden = () -> { throw new AssertionError("successful selection rendered its reason"); };

        assertEquals(1, waiting.until(first(instanceOf(User.class).because(forbidden)).because(forbidden)).id());
        assertEquals(2, waiting.until(last(instanceOf(User.class)).because("last %s", "user")).id());
        assertEquals(1, waiting.until(element(1, instanceOf(User.class)).because(forbidden)).id());
        AwaitResult<List<Object>, User> captured = waiting.tryUntil(first(instanceOf(User.class)).because("first"));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var user) -> assertEquals(1, user.id());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
    }

    @Test
    void checksAndComparisonsRetainTheSourceElementType() {
        List<User> values = List.of(new User(-1), FIRST, LAST);
        var time = new FakeTime(0);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        Supplier<String> forbidden = () -> { throw new AssertionError("successful check rendered its reason"); };

        assertEquals(1, waiting.until(first(matches(User::positive).because(forbidden)).because(forbidden)).id());
        assertEquals(2, waiting.until(last(matches(User::positive)).because("positive %s", "user")).id());
        assertEquals(1, waiting.until(element(1, matches(User::positive)).because(forbidden)).id());
        assertEquals(2, waiting.until(first(equalTo(LAST).because(forbidden)).because("first match")).id());
        assertEquals(1, waiting.until(last(equalTo(FIRST)).because(forbidden)).id());
        assertEquals(2, waiting.until(element(2, equalTo(LAST)).because(forbidden)).id());
        assertEquals(-1, waiting.until(first(isNotNull).because(forbidden)).id());
        assertEquals(2, waiting.until(last(isNotNull)).id());
        assertEquals(1, waiting.until(element(1, isNotNull)).id());

        var selected = waiting.until(first(matches(User::positive)), last(equalTo(LAST)), element(1, isNotNull));
        assertEquals(1, selected.getFirst().id());
        assertEquals(1, selected.getLast().id());
        AwaitResult<List<User>, User> captured = waiting.tryUntil(last(equalTo(FIRST)).because("selected"));
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var user) -> assertEquals(1, user.id());
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
        List<CharSequence> texts = List.of("ready");
        assertEquals(5, await(() -> texts).usingTime(time, time).until(first(equalTo("ready")).because("text")).length());
    }

    @Test
    void nestedCheckStateIsRetainedAcrossRetriesAndResetForEachWait() {
        int[] factories = {0};
        Condition.PreservingCondition<User> secondObservation = Conditions.condition("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? ConditionResult.satisfied(value) : ConditionResult.unsatisfied("one more");
        });
        List<Condition.SelectedCondition<? super List<User>>> selectors = List.of(
                first(secondObservation), last(secondObservation), element(0, secondObservation));
        var time = new FakeTime(0);
        int[] reads = {0};
        var waiting = await(() -> { reads[0]++; return List.of(FIRST); })
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        for (var selector : selectors) {
            for (int run = 0; run < 2; run++) {
                assertEquals(1, waiting.until(selector.because("stateful")).id());
            }
        }
        assertEquals(6, factories[0]);
        assertEquals(12, reads[0]);

        assertEquals(1, await(() -> List.of(new User(-1), FIRST, LAST)).usingTime(time, time)
                .until(first(secondObservation)).id());
        assertEquals(7, factories[0]);
    }

    @Test
    void predicateSearchStopsBeforeTheNextCallbackAfterInterruption() {
        for (boolean reverse : List.of(false, true)) {
            int[] calls = {0};
            Predicate<User> predicate = value -> {
                calls[0]++;
                Thread.currentThread().interrupt();
                return false;
            };
            var selection = reverse ? last(predicate::test) : first(predicate::test);
            var time = new FakeTime(0);
            try {
                var result = await(() -> List.of(FIRST, LAST)).usingTime(time, time).tryUntil(selection);
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.reason());
                assertInstanceOf(InterruptedException.class, failure.cause());
                assertEquals(1, calls[0]);
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(1, result.attempts().size());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void typedSelectionsComposeWithOtherContainers() {
        List<List<Object>> nested = List.of(List.of("skip"), List.of(FIRST), List.of(LAST));
        var user = waiting(() -> nested).until(first(first(instanceOf(User.class))).because("nested"));
        assertEquals(1, user.id());
        assertEquals(2, waiting(() -> nested).until(last(last(instanceOf(User.class)))).id());
        assertEquals(1, waiting(() -> Optional.of(nested.get(1))).until(
                OptionalConditions.hasValue(first(instanceOf(User.class))).because("optional")).id());
        assertEquals(2, waiting(() -> Map.of("users", nested.get(2))).until(
                MapConditions.valueFor("users", element(0, instanceOf(User.class)))).id());
        assertEquals(1, waiting(() -> nested.subList(0, 2)).until(single(first(instanceOf(User.class)))).id());
    }

    @Test
    void orderedSelectionsCaptureValuesFromSeparateObservations() {
        List<Object> values = List.of(FIRST, "skip", LAST);
        int[] reads = {0};
        var waiting = waiting(() -> { reads[0]++; return values; });
        var firstUser = first(instanceOf(User.class)).because("first");
        var lastUser = last(instanceOf(User.class)).because("last");
        var selected = waiting.until(firstUser, lastUser);

        assertEquals(2, reads[0]);
        assertSame(FIRST, selected.getFirst());
        assertEquals(2, selected.getLast().id());
        assertThrows(UnsupportedOperationException.class, selected::clear);
        AwaitResult<List<Object>, List<User>> captured = waiting.tryUntil(firstUser, lastUser);
        switch (captured) {
            case AwaitResult.Satisfied(var attempts, var users) -> {
                assertEquals(1, users.getFirst().id());
                assertSame(LAST, users.getLast());
            }
            case AwaitResult.Failed(var attempts, var failure) -> fail(failure.message());
        }
        assertEquals(4, reads[0]);
    }

    @Test
    void searchesStopAtTheFirstMatchInTheirTraversalOrder() {
        var firstReads = new ArrayList<Integer>();
        var firstValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                firstReads.add(index);
                if (index != 0) throw new AssertionError("first searched beyond its match");
                return FIRST;
            }
        };
        assertEquals(1, waiting(() -> firstValues).until(first(instanceOf(User.class))).id());
        assertEquals(List.of(0), firstReads);

        var lastReads = new ArrayList<Integer>();
        var lastValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                lastReads.add(index);
                if (index != 1) throw new AssertionError("last searched beyond its match");
                return LAST;
            }
        };
        assertEquals(2, waiting(() -> lastValues).until(last(instanceOf(User.class))).id());
        assertEquals(List.of(1), lastReads);

        var indexReads = new ArrayList<Integer>();
        var indexed = new AbstractList<Object>() {
            @Override public int size() { return 3; }
            @Override public Object get(int index) {
                indexReads.add(index);
                return switch (index) {
                    case 0 -> "skip";
                    case 1 -> FIRST;
                    default -> throw new AssertionError("element searched beyond its index");
                };
            }
        };
        assertEquals(1, waiting(() -> indexed).until(element(1, instanceOf(User.class))).id());
        assertEquals(List.of(0, 1), indexReads);
    }

    @Test
    void mismatchesRetainLazyReasonsAndExtractionDetails() {
        List<Object> values = List.of("wrong");
        List<Condition<? super List<Object>, User>> checks = List.of(
                first(instanceOf(User.class)), last(instanceOf(User.class)), element(0, instanceOf(User.class)));
        for (var check : checks) {
            int[] reasons = {0};
            var result = waiting(() -> values).tryUntil(check.because(() -> { reasons[0]++; return "user required"; }));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Because: user required"));
            assertTrue(failure.message().contains("value was not an instance of " + User.class.getTypeName()));
            assertEquals(1, reasons[0]);
        }
        var missing = waiting(() -> values).tryUntil(element(3, instanceOf(User.class).because("inner")).because("outer"));
        var failure = assertInstanceOf(AwaitResult.Failed.class, missing).failure();
        assertTrue(failure.message().contains("index 3"));
        assertTrue(failure.message().contains("list size was 1"));
        assertTrue(failure.message().contains("Because: outer"));
        assertThrows(IllegalArgumentException.class, () -> element(-1, instanceOf(User.class)));

        Condition<Object, User> absent = null;
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> first(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> last(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> element(0, absent)).getMessage());
        List<Object> noValues = null;
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class,
                waiting(() -> noValues).tryUntil(first(instanceOf(User.class)))).failure().reason());
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class,
                waiting(List::of).tryUntil(last(instanceOf(User.class)))).failure().reason());
    }

    @Test
    void nestedFailuresStopSearchAndRetainTheirCauseAndCondition() {
        for (Throwable cause : List.of(new IllegalStateException("getter failed"), new AssertionError("getter failed"))) {
            Map<String, Object> broken = new AbstractMap<>() {
                @Override public Set<Map.Entry<String, Object>> entrySet() {
                    return Set.of(new SimpleEntry<>("target-key", FIRST) {
                        @Override public Object getValue() { return throwUnchecked(cause); }
                    });
                }
                @Override public String toString() { return "broken map"; }
            };
            Map<String, Object> valid = Map.of("target-key", LAST);
            var lookup = MapConditions.valueFor("target-key", instanceOf(User.class));
            assertNestedFailure(first(lookup), List.of(broken, valid), cause);
            assertNestedFailure(last(lookup), List.of(valid, broken), cause);
            assertNestedFailure(element(0, lookup), List.of(broken, valid), cause);
        }
    }

    private static void assertNestedFailure(Condition<? super List<Map<String, Object>>, User> condition,
            List<Map<String, Object>> values, Throwable cause) {
        int[] reads = {0};
        var result = waiting(() -> { reads[0]++; return values; }).tryUntil(condition.because("selected user"));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
        assertSame(cause, failure.cause());
        assertEquals(1, reads[0]);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, result.attempts().getLast().outcome());
        assertEquals("target-key", attempt.condition().reference().value());
    }

    private static <OBSERVED> ValueAwait<OBSERVED> waiting(Callable<OBSERVED> source) {
        var time = new FakeTime(0);
        return await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
    }
}
