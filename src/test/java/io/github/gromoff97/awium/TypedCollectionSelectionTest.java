package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class TypedCollectionSelectionTest {

    private record User(int id) {
        boolean positive() { return id > 0; }
    }
    private static final User FIRST = new User(1);
    private static final User LAST = new User(2);

    @Test
    void indexedSelectionsAndCapturesAcceptEveryOrderedCollectionKind() {
        var time = new FakeTime(0);
        var queue = new ArrayDeque<>(List.of(FIRST, LAST));
        var waiting = await(() -> queue).usingTime(time, time).every(ofNanos(1));

        assertSame(LAST, waiting.until(elementAt(1)));
        assertEquals(2, waiting.until(elementAt(1, User::positive)).id());
        assertEquals(2, waiting.until(elementAt(1, matches(User::positive))).id());
        assertSame(LAST, waiting.until(elementAt(1, equalTo(LAST)).because("the second user must arrive")));
        assertEquals(2, diagnostic(waiting).until(elementAt(1, instanceOf(User.class))).orThrow().id());

        var sequence = captured(firstElement, elementAt(1), lastElement).because("all positions must exist");
        var queues = diagnostic(waiting).until(sequence).orThrow();
        assertEquals(3, queues.size());
        assertEquals(2, queues.getLast().peekLast().id());
        assertTrue(queues.stream().allMatch(observed -> observed == queue));

        var orderedSet = new LinkedHashSet<>(List.of(FIRST, LAST));
        var sets = await(() -> orderedSet).usingTime(time, time).every(ofNanos(1)).until(sequence);
        assertEquals(3, sets.size());
        assertEquals(2, sets.getLast().getLast().id());
        assertTrue(sets.stream().allMatch(observed -> observed == orderedSet));
        assertSame(FIRST, await(queue::reversed).until(elementAt(1)));

        var missing = diagnostic(waiting.upTo(ofNanos(2))).until(elementAt(2));
        var failure = assertInstanceOf(AwaitResult.Failed.class, missing).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("index 2"));
        assertTrue(failure.message().contains("collection size was 2"));
    }

    @Test
    void firstLastAndIndexedConditionsReturnTheirDeclaredValues() {
        List<Object> values = List.of(FIRST, 99, LAST);
        var time = new FakeTime(0);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(1, waiting.until(firstElement(instanceOf(User.class))).id());
        assertEquals(2, waiting.until(lastElement(instanceOf(User.class))).id());
        assertEquals(1, waiting.until(elementAt(0, instanceOf(User.class))).id());
        AwaitResult<List<Object>, User> captured = diagnostic(waiting).until(firstElement(instanceOf(User.class)));
        switch (captured) {
            case AwaitResult.Satisfied(var history, var user) -> assertEquals(1, user.id());
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
    }

    @Test
    void checksAndComparisonsRetainTheSourceElementType() {
        List<User> values = List.of(FIRST, new User(-1), LAST);
        var time = new FakeTime(0);
        var waiting = await(() -> values).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(1, waiting.until(firstElement(matches(User::positive))).id());
        assertEquals(2, waiting.until(lastElement(matches(User::positive))).id());
        assertEquals(1, waiting.until(elementAt(0, matches(User::positive))).id());
        assertEquals(1, waiting.until(firstElement(equalTo(FIRST))).id());
        assertEquals(2, waiting.until(lastElement(equalTo(LAST))).id());
        assertEquals(2, waiting.until(elementAt(2, equalTo(LAST))).id());
        assertEquals(1, waiting.until(firstElement(isNotNull)).id());
        assertEquals(2, waiting.until(lastElement(isNotNull)).id());
        assertEquals(1, waiting.until(elementAt(0, isNotNull)).id());

        var selected = waiting.until(Conditions.<List<User>>captured(firstElement(matches(User::positive)), lastElement(equalTo(LAST)), elementAt(0, isNotNull)));
        assertSame(values, selected.getFirst());
        assertSame(values, selected.getLast());
        AwaitResult<List<User>, User> captured = diagnostic(waiting).until(lastElement(equalTo(LAST)));
        switch (captured) {
            case AwaitResult.Satisfied(var history, var user) -> assertEquals(2, user.id());
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
        List<CharSequence> texts = List.of("ready");
        assertEquals(5, await(() -> texts).usingTime(time, time).until(firstElement(equalTo("ready"))).length());
    }

    @Test
    void nestedCheckStateIsRetainedAcrossRetriesAndResetForEachWait() {
        int[] factories = {0};
        Condition.PreservingCondition<User> secondObservation = Conditions.conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? ConditionResult.satisfied() : ConditionResult.unsatisfied("one more");
        });
        List<Condition.SelectedCondition<? super List<User>>> selectors = List.of(
                firstElement(secondObservation), lastElement(secondObservation), elementAt(0, secondObservation));
        var time = new FakeTime(0);
        int[] reads = {0};
        var waiting = await(() -> { reads[0]++; return List.of(FIRST); })
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        for (var selector : selectors) {
            for (int run = 0; run < 2; run++) {
                assertEquals(1, waiting.until(selector).id());
            }
        }
        assertEquals(6, factories[0]);
        assertEquals(12, reads[0]);

        var retried = tryAwait(() -> List.of(new User(-1), FIRST, LAST)).usingTime(time, time).every(ofNanos(1))
                .until(firstElement(secondObservation));
        switch (retried) {
            case AwaitResult.Satisfied(var history, var user) -> assertEquals(-1, user.id());
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(2, retried.attempts().size());
        assertEquals(7, factories[0]);
    }

    @Test
    void interruptionDuringTheNestedPredicateStopsTheWait() {
        for (boolean reverse : List.of(false, true)) {
            int[] calls = {0};
            Predicate<User> predicate = value -> {
                calls[0]++;
                Thread.currentThread().interrupt();
                return false;
            };
            var selection = reverse ? lastElement(predicate::test) : firstElement(predicate::test);
            var time = new FakeTime(0);
            try {
                var result = tryAwait(() -> List.of(FIRST, LAST)).usingTime(time, time).until(selection);
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
        List<List<Object>> nested = List.of(List.of(FIRST), List.of("skip"), List.of(LAST));
        var user = waiting(() -> nested).until(firstElement(firstElement(instanceOf(User.class))));
        assertEquals(1, user.id());
        assertEquals(2, waiting(() -> nested).until(lastElement(lastElement(instanceOf(User.class)))).id());
        assertEquals(1, waiting(() -> Optional.of(nested.getFirst())).until(
                OptionalConditions.hasValue(firstElement(instanceOf(User.class)))).id());
        assertEquals(2, waiting(() -> Map.of("users", nested.get(2))).until(
                MapConditions.valueFor("users", elementAt(0, instanceOf(User.class)))).id());
        assertEquals(1, waiting(() -> nested.subList(0, 1)).until(singleElement(firstElement(instanceOf(User.class)))).id());
    }

    @Test
    void orderedSelectionsCaptureTheCompleteObservationAtEachStage() {
        List<Object> values = List.of(FIRST, "skip", LAST);
        int[] reads = {0};
        var waiting = waiting(() -> { reads[0]++; return values; });
        var firstUser = firstElement(instanceOf(User.class));
        var lastUser = lastElement(instanceOf(User.class));
        var selected = waiting.until(captured(firstUser, lastUser));

        assertEquals(2, reads[0]);
        assertSame(values, selected.getFirst());
        assertSame(values, selected.getLast());
        assertThrows(UnsupportedOperationException.class, selected::clear);
        AwaitResult<List<Object>, List<List<Object>>> captured = diagnostic(waiting).until(captured(firstUser, lastUser));
        switch (captured) {
            case AwaitResult.Satisfied(var history, var users) -> {
                assertSame(values, users.getFirst());
                assertSame(values, users.getLast());
            }
            case AwaitResult.Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(4, reads[0]);
    }

    @Test
    void selectorsReadOnlyAsFarAsTheirPosition() {
        var firstReads = new ArrayList<Integer>();
        var firstValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                firstReads.add(index);
                if (index != 0) throw new AssertionError("first read beyond its position");
                return FIRST;
            }
        };
        assertEquals(1, waiting(() -> firstValues).until(firstElement(instanceOf(User.class))).id());
        assertEquals(List.of(0), firstReads);

        var lastReads = new ArrayList<Integer>();
        var lastValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                lastReads.add(index);
                if (index != 1) throw new AssertionError("last read beyond its position");
                return LAST;
            }
        };
        assertEquals(2, waiting(() -> lastValues).until(lastElement(instanceOf(User.class))).id());
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
        assertEquals(1, waiting(() -> indexed).until(elementAt(1, instanceOf(User.class))).id());
        assertEquals(List.of(0, 1), indexReads);
    }

    @Test
    void mismatchesRetainExtractionDetails() {
        List<Object> values = List.of("wrong", FIRST, "wrong");
        List<Condition.Typed<? super List<Object>, User>> checks = List.of(
                firstElement(instanceOf(User.class)), lastElement(instanceOf(User.class)), elementAt(0, instanceOf(User.class)));
        for (var check : checks) {
            var result = diagnostic(waiting(() -> values)).until(check);
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("value was not an instance of " + User.class.getTypeName()));
        }
        var missing = diagnostic(waiting(() -> values)).until(elementAt(3, instanceOf(User.class)));
        var failure = assertInstanceOf(AwaitResult.Failed.class, missing).failure();
        assertTrue(failure.message().contains("index 3"));
        assertTrue(failure.message().contains("collection size was 3"));
        assertThrows(IllegalArgumentException.class, () -> elementAt(-1, instanceOf(User.class)));

        Condition.Typed<Object, User> absent = null;
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> firstElement(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> lastElement(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> elementAt(0, absent)).getMessage());
        List<Object> noValues = null;
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class,
                diagnostic(waiting(() -> noValues)).until(firstElement(instanceOf(User.class)))).failure().reason());
        assertEquals(AwaitFailure.Reason.TIMEOUT, assertInstanceOf(AwaitResult.Failed.class,
                diagnostic(waiting(List::of)).until(lastElement(instanceOf(User.class)))).failure().reason());
    }

    @Test
    void nestedFailuresRetainTheirCauseAndCondition() {
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
            assertNestedFailure(firstElement(lookup), List.of(broken, valid), cause);
            assertNestedFailure(lastElement(lookup), List.of(valid, broken), cause);
            assertNestedFailure(elementAt(0, lookup), List.of(broken, valid), cause);
        }
    }

    private static void assertNestedFailure(Condition.Typed<? super List<Map<String, Object>>, User> condition,
            List<Map<String, Object>> values, Throwable cause) {
        int[] reads = {0};
        var result = diagnostic(waiting(() -> { reads[0]++; return values; })).until(condition);
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
        assertSame(cause, failure.cause());
        assertEquals(1, reads[0]);
        var attempt = result.attempts().getLast().last();
        assertEquals("target-key", attempt.condition().reference().value());
    }

    private static <OBSERVED> ValueAwait<OBSERVED> waiting(Callable<OBSERVED> source) {
        var time = new FakeTime(0);
        return await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
    }
}
