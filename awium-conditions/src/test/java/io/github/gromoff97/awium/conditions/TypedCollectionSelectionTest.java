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


    @AfterEach
    void clearInterruptFlag() {
        interrupted();
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
