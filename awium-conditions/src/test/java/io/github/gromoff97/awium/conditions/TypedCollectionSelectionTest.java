package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static java.util.Map.Entry;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TypedCollectionSelectionTest {

    private record User(int id) {
        boolean positive() { return id > 0; }
    }
    private static final User FIRST = new User(1);
    private static final User LAST = new User(2);

    @Test
    void captureReasonsKeepMixedSelectorAndComparisonTypes() {
        var values = List.of(FIRST);
        var waiting = await(() -> values).every(ofNanos(1));
        var selectors = Conditions.<List<User>>captured(
                singleElement(equalTo(FIRST)), singleElement(User::positive)).because("selected user is valid");
        var comparison = captured(equalTo(values), singleElement(User::positive)).because("whole list and user agree");

        assertThat(waiting.until(selectors)).containsExactly(values, values);
        assertThat(tryAwait(() -> values).every(ofNanos(1)).until(selectors).orThrow()).containsExactly(values, values);
        assertThat(waiting.until(comparison)).containsExactly(values, values);
        assertThat(tryAwait(() -> values).every(ofNanos(1)).until(comparison).orThrow()).containsExactly(values, values);
    }

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void indexedSelectionsAndCapturesAcceptEveryOrderedCollectionKind() {
        var queue = new ArrayDeque<>(List.of(FIRST, LAST));
        var waiting = await(() -> queue).every(ofNanos(1));

        assertSame(LAST, waiting.until(elementAt(1)));
        assertEquals(2, waiting.until(elementAt(1, User::positive)).id());
        assertEquals(2, waiting.until(elementAt(1, matches(User::positive))).id());
        assertSame(LAST, waiting.until(elementAt(1, equalTo(LAST)).because("the second user must arrive")));
        assertEquals(2, tryAwait(() -> queue).every(ofNanos(1)).until(elementAt(1, instanceOf(User.class))).orThrow().id());

        var sequence = captured(firstElement, elementAt(1), lastElement).because("all positions must exist");
        var queues = tryAwait(() -> queue).every(ofNanos(1)).until(sequence).orThrow();
        assertThat(queues).hasSize(3).allSatisfy(observed -> assertSame(queue, observed));
        assertEquals(2, queues.getLast().peekLast().id());

        var orderedSet = new LinkedHashSet<>(List.of(FIRST, LAST));
        var sets = await(() -> orderedSet).every(ofNanos(1)).until(sequence);
        assertThat(sets).hasSize(3).allSatisfy(observed -> assertSame(orderedSet, observed));
        assertEquals(2, sets.getLast().getLast().id());
        assertSame(FIRST, await(queue::reversed).until(elementAt(1)));

        var failure = rejection(elementAt(2), queue);
        assertEquals("collection size was 2", failure.mismatch());
        assertThat(elementAt(2).description()).contains("index 2");
    }

    @Test
    void firstLastAndIndexedConditionsReturnTheirDeclaredValues() {
        List<Object> values = List.of(FIRST, 99, LAST);
        var waiting = await(() -> values).every(ofNanos(1));

        assertEquals(1, waiting.until(firstElement(instanceOf(User.class))).id());
        assertEquals(2, waiting.until(lastElement(instanceOf(User.class))).id());
        assertEquals(1, waiting.until(elementAt(0, instanceOf(User.class))).id());
        AwaitResult<List<Object>, User> captured = tryAwait(() -> values).every(ofNanos(1)).until(firstElement(instanceOf(User.class)));
        switch (captured) {
            case Succeeded(var history, var user) -> assertEquals(1, user.id());
            case Failed(var history, var failure) -> fail(failure.message());
        }
    }

    @Test
    void checksAndComparisonsRetainTheSourceElementType() {
        List<User> values = List.of(FIRST, new User(-1), LAST);
        var waiting = await(() -> values).every(ofNanos(1));

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
        AwaitResult<List<User>, User> captured = tryAwait(() -> values).every(ofNanos(1)).until(lastElement(equalTo(LAST)));
        switch (captured) {
            case Succeeded(var history, var user) -> assertEquals(2, user.id());
            case Failed(var history, var failure) -> fail(failure.message());
        }
        List<CharSequence> texts = List.of("ready");
        assertEquals(5, await(() -> texts).until(firstElement(equalTo("ready"))).length());
    }

    @Test
    void nestedCheckStateIsRetainedAcrossRetriesAndResetForEachWait() {
        int[] factories = {0};
        Condition<User, User> secondObservation = conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? satisfied(value) : unsatisfied("one more");
        });
        List<Condition<? super List<User>, User>> selectors = List.of(
                firstElement(secondObservation), lastElement(secondObservation), elementAt(0, secondObservation));
        int[] reads = {0};
        var waiting = await(() -> { reads[0]++; return List.of(FIRST); })
                .every(ofNanos(1));
        for (var selector : selectors) {
            for (int run = 0; run < 2; run++) {
                assertEquals(1, waiting.until(selector).id());
            }
        }
        assertEquals(6, factories[0]);
        assertEquals(12, reads[0]);

        var retried = tryAwait(() -> List.of(new User(-1), FIRST, LAST)).every(ofNanos(1))
                .until(firstElement(secondObservation));
        switch (retried) {
            case Succeeded(var history, var user) -> assertEquals(-1, user.id());
            case Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(2, attempts(retried).size());
        assertEquals(7, factories[0]);
    }

    @Test
    void interruptionDuringTheFirstElementPredicateStopsTheWait() {
        assertInterruptedSelection(predicate -> firstElement(predicate::test));
    }

    @Test
    void interruptionDuringTheLastElementPredicateStopsTheWait() {
        assertInterruptedSelection(predicate -> lastElement(predicate::test));
    }

    private void assertInterruptedSelection(Function<Predicate<User>, SelectedCondition<SequencedCollection<? extends User>>> select) {
        int[] calls = {0};
        Predicate<User> predicate = value -> {
            calls[0]++;
            currentThread().interrupt();
            return false;
        };
        var result = tryAwait(() -> List.of(FIRST, LAST)).until(select.apply(predicate));
        var failure = failure(INTERRUPTED, result);
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertEquals(1, calls[0]);
        assertTrue(currentThread().isInterrupted());
        assertEquals(1, attempts(result).size());
    }

    @Test
    void typedSelectionsComposeWithOtherContainers() {
        List<List<Object>> nested = List.of(List.of(FIRST), List.of("skip"), List.of(LAST));
        var user = waiting(() -> nested).until(firstElement(firstElement(instanceOf(User.class))));
        assertEquals(1, user.id());
        assertEquals(2, waiting(() -> nested).until(lastElement(lastElement(instanceOf(User.class)))).id());
        assertEquals(1, waiting(() -> Optional.of(nested.getFirst())).until(
                hasValue(firstElement(instanceOf(User.class)))).id());
        assertEquals(2, waiting(() -> Map.of("users", nested.get(2))).until(
                valueFor("users", elementAt(0, instanceOf(User.class)))).id());
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
        AwaitResult<List<Object>, List<List<Object>>> captured = tryAwait(() -> { reads[0]++; return values; }).every(ofNanos(1)).until(captured(firstUser, lastUser));
        switch (captured) {
            case Succeeded(var history, var users) -> {
                assertSame(values, users.getFirst());
                assertSame(values, users.getLast());
            }
            case Failed(var history, var failure) -> fail(failure.message());
        }
        assertEquals(4, reads[0]);
    }

    @Test
    void firstElementReadsOnlyItsPosition() {
        var firstReads = new ArrayList<Integer>();
        var firstValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                firstReads.add(index);
                assertEquals(0, index, "first read beyond its position");
                return FIRST;
            }
        };
        assertEquals(1, waiting(() -> firstValues).until(firstElement(instanceOf(User.class))).id());
        assertEquals(List.of(0), firstReads);
    }

    @Test
    void lastElementReadsOnlyItsPosition() {
        var lastReads = new ArrayList<Integer>();
        var lastValues = new AbstractList<Object>() {
            @Override public int size() { return 2; }
            @Override public Object get(int index) {
                lastReads.add(index);
                assertEquals(1, index, "last read beyond its position");
                return LAST;
            }
        };
        assertEquals(2, waiting(() -> lastValues).until(lastElement(instanceOf(User.class))).id());
        assertEquals(List.of(1), lastReads);
    }

    @Test
    void indexedElementStopsReadingAtItsPosition() {
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
        List<Condition<? super List<Object>, User>> checks = List.of(
                firstElement(instanceOf(User.class)), lastElement(instanceOf(User.class)), elementAt(0, instanceOf(User.class)));
        for (var check : checks) {
            var failure = rejection(check, values);
            assertThat(failure.mismatch()).contains("value type was java.lang.String");
        }
        var check = elementAt(3, instanceOf(User.class));
        assertEquals("collection size was 3", rejection(check, values).mismatch());
        assertThat(check.description()).contains("index 3");
        assertThrows(IllegalArgumentException.class, () -> elementAt(-1, instanceOf(User.class)));

        Condition<Object, User> absent = null;
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> firstElement(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> lastElement(absent)).getMessage());
        assertEquals("condition must not be null", assertThrows(NullPointerException.class, () -> elementAt(0, absent)).getMessage());
        List<Object> noValues = null;
        rejection(firstElement(instanceOf(User.class)), noValues);
        rejection(lastElement(instanceOf(User.class)), List.of());
    }

    @Test
    void nestedFailuresRetainTheirCauseAndCondition() {
        for (Throwable cause : List.of(new IllegalStateException("getter failed"), new AssertionError("getter failed"))) {
            Map<String, Object> broken = new AbstractMap<>() {
                @Override public Set<Entry<String, Object>> entrySet() {
                    return Set.of(new SimpleEntry<>("target-key", FIRST) {
                        @Override public Object getValue() { return rethrow(cause); }
                    });
                }
                @Override public String toString() { return "broken map"; }
            };
            Map<String, Object> valid = Map.of("target-key", LAST);
            var lookup = valueFor("target-key", instanceOf(User.class));
            assertNestedFailure(firstElement(lookup), List.of(broken, valid), cause);
            assertNestedFailure(lastElement(lookup), List.of(valid, broken), cause);
            assertNestedFailure(elementAt(0, lookup), List.of(broken, valid), cause);
        }
    }

    private static void assertNestedFailure(Condition<? super List<Map<String, Object>>, User> condition, List<Map<String, Object>> values, Throwable cause) {
        int[] reads = {0};
        var result = tryAwait(() -> { reads[0]++; return values; }).until(condition);
        var failure = failure(CONDITION_FAILED, result);
        assertSame(cause, failure.cause());
        assertEquals(1, reads[0]);
        assertEquals("target-key", result.conditions().getFirst().condition().references().getFirst().value());
    }

    private static <OBSERVED> ValueAwait<OBSERVED> waiting(Callable<OBSERVED> source) {

        return await(source).every(ofNanos(1));
    }
}
