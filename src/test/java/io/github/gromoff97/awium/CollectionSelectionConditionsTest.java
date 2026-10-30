package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.CollectionConditions.subsetOf;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionSelectionConditionsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var users = new ArrayList<>(List.of(new User(2)));
        java.util.concurrent.Callable<ArrayList<User>> source = () -> users;

        User matching = await(source::call).usingTime(time, time).until(CollectionConditions.singleElement(matches(user -> user.id() == 2)));
        String typed = await((java.util.concurrent.Callable<List<Object>>) () -> List.of("text")).usingTime(time, time).until(CollectionConditions.singleElement(instanceOf(String.class)));
        String nullable = await(() -> Arrays.asList((String) null)).usingTime(time, time).until(CollectionConditions.singleElement);

        assertSame(users.getFirst(), matching);
        assertEquals("text", typed);
        assertNull(nullable);
        assertNull(await(() -> singletonList((String) null)).usingTime(time, time).until(CollectionConditions.singleElement(matches(value -> value == null))));
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var actual = new ArrayList<>(List.of(2, 4, 6));
        java.util.concurrent.Callable<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source::call).usingTime(time, time).until(CollectionConditions.all(value -> value % 2 == 0)));
        assertSame(actual, await(source::call).usingTime(time, time).until(CollectionConditions.any(value -> value == 4)));
        assertSame(actual, await(source::call).usingTime(time, time).until(CollectionConditions.none(value -> value < 0)));
    }

    @Test
    void quantifiersCoverTheirUnsatisfiedBranches() {
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.all(
                (Integer value) -> value % 2 == 0), List.of(2, 3)));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.any(
                (Integer value) -> value == 4), List.of(1, 3)));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.none(
                (Integer value) -> value < 0), List.of(1, -1)));
    }

    @Test
    void subsetPreservesTypedCollectionsAcrossTerminalsAndComposition() {
        var actual = new ArrayList<>(List.of("ready"));
        var plain = await(() -> actual).until(subsetOf(List.of("ready", "done")));
        var selected = await(() -> List.of(actual)).until(singleElement(subsetOf(List.of("ready"))));
        var captured = await(() -> actual).usingTime(time, time)
                .until(captured(subsetOf(List.of("ready")), subsetOf(List.of("ready", "done"))));

        assertSame(actual, plain);
        assertSame(actual, selected);
        assertSame(actual, captured.getLast());
        assertEquals(List.of(5, 5, 5), List.of(plain.getFirst().length(),
                selected.getFirst().length(), captured.getLast().getFirst().length()));

        var diagnostic = tryAwait(() -> actual).until(subsetOf(List.of("ready")));
        if (diagnostic instanceof AwaitResult.Satisfied(var history, var value)) {
            assertSame(actual, value);
            assertEquals(5, value.getFirst().length());
        } else {
            throw new AssertionError(diagnostic);
        }

        List<CharSequence> text = List.of("ready");
        assertSame(text, await(() -> text).until(subsetOf(List.of("ready"))));
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() {
        var values = new ArrayList<>(Arrays.asList("a", "a", null));

        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.containsNull, values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.doesNotContainNull, values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.hasNoDuplicates, values));
        assertInstanceOf(Satisfied.class,
                evaluate(CollectionConditions.containsOnly(asList("a", null)), values));
        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.subsetOf(
                Arrays.asList("a", "b", null)), values));
        assertInstanceOf(Satisfied.class,
                evaluate(CollectionConditions.hasSizeBetween(2, 4), values));
        assertInstanceOf(Satisfied.class, evaluate(
                CollectionConditions.hasSize(3), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.containsOnlyNulls, List.of()));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.containsNull, List.of("a")));
        assertInstanceOf(Satisfied.class,
                evaluate(CollectionConditions.doesNotContainNull, List.of("a")));
        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, null)));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, "a")));
        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.hasNoDuplicates,
                List.of("a", "b")));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.containsOnly(asList("a", null)),
                Arrays.asList("a", "b", null)));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.subsetOf(
                List.of("a", "b")), List.of("a", "c")));
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() {
        var pollingTime = new FakeTime(0);
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        java.util.concurrent.Callable<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source::call).usingTime(pollingTime, pollingTime).until(firstElement));
        assertEquals(5, await(source::call).usingTime(pollingTime, pollingTime).until(lastElement));
        assertEquals(3, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.elementAt(2)));
        assertEquals(1, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.firstElement(value -> value > 0)));
        assertEquals(5, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.lastElement(value -> value < 6)));
        assertSame(values, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.startsWith(List.of(1, 2))));
        assertSame(values, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.endsWith(List.of(3, 5))));
        var pair = List.of(1, 2);
        var exact = await(() -> pair).usingTime(pollingTime, pollingTime).upTo(java.time.Duration.ofNanos(1));
        assertSame(pair, exact.until(CollectionConditions.startsWith(List.of(1, 2))));
        assertSame(pair, exact.until(CollectionConditions.endsWith(List.of(1, 2))));
        assertSame(values, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSequence(List.of(2, 3))));
        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.containsSequence(List.of(1, 2)),
                List.of(1, 2)));
        assertSame(values, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSubsequence(List.of(1, 3, 5))));
        assertSame(values, await(source::call).usingTime(pollingTime, pollingTime).until(CollectionConditions.sorted(Integer::compareTo)));

        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.startsWith(List.of(1, 3)), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.endsWith(List.of(2, 5)), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(CollectionConditions.containsSequence(List.of(1, 3)), values));
        assertInstanceOf(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(List.of(1, 3)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(List.of(2, 3)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                CollectionConditions.containsSubsequence(List.of(1, 4)), values));
        assertInstanceOf(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(List.of(1, 4)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(List.of(1, 3, 5)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.sorted(Integer::compareTo),
                List.of(1, 3, 2)));
        var equalElements = List.of(1, 1);
        assertSame(equalElements, await(() -> equalElements).usingTime(pollingTime, pollingTime)
                .until(CollectionConditions.sorted(Integer::compareTo)));
        assertInstanceOf(Satisfied.class, evaluate(CollectionConditions.sorted(
                java.util.Comparator.comparingInt(Integer::intValue).reversed()), List.of(3, 2, 1)));

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await(() -> sequencedSet).usingTime(pollingTime, pollingTime).until(firstElement));
        assertSame(sequencedSet, await(() -> sequencedSet).usingTime(pollingTime, pollingTime).until(
                CollectionConditions.startsWith(List.of(1, 2))));
    }

    @Test
    void singleElementRequiresOneElementBeforeApplyingTheCheck() {
        var waiting = await(() -> List.of(1, 2)).usingTime(time, time).upTo(ofNanos(1));

        var none = assertFailure(TIMEOUT, () -> await(() -> List.of(1)).usingTime(time, time).upTo(ofNanos(1))
                .until(singleElement(matches(value -> value > 10))));
        var many = assertFailure(TIMEOUT, () -> waiting.until(singleElement(matches(value -> value == 2))));

        assertThat(none.getMessage()).contains("value did not match");
        assertThat(many.getMessage()).contains("collection size was greater than 1");
    }

    @Test
    void emptyOrderedPatternsMatchEmptyAndNonEmptyCollections() {
        List<Condition.PreservingCondition<SequencedCollection<? super String>>> positive = List.of(
                CollectionConditions.startsWith(List.of()), CollectionConditions.endsWith(List.of()),
                CollectionConditions.containsSequence(List.of()), CollectionConditions.containsSubsequence(List.of()));
        List<Condition.PreservingCondition<SequencedCollection<? super String>>> negative = List.of(
                CollectionConditions.doesNotContainSequence(List.of()), CollectionConditions.doesNotContainSubsequence(List.of()));
        for (var checks : List.of(positive, negative)) {
            for (var check : checks) assertInstanceOf(Unsatisfied.class, evaluate(check, null));
        }

        for (var values : List.of(List.<String>of(), List.of("a", "b"))) {
            var actual = new ArrayList<>(values);
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            for (var check : positive) {
                ArrayList<String> selected = waiting.until(check);
                assertSame(actual, selected);
                assertSame(actual, diagnostic(waiting).until(check).orThrow());
            }
            for (var check : negative) {
                var failed = diagnostic(waiting).until(check.because("pattern must be absent"));
                var failure = failure(failed);
                assertEquals(TIMEOUT, failure.reason());
                assertThat(failure.message()).contains("Because: pattern must be absent");
                assertEquals(List.of(), failed.conditions().getLast().condition().references().getFirst().value());
            }
        }
    }

    @Test
    void emptyOrderedPatternsDoNotReadTheObservedCollection() {
        var actual = new AbstractList<Object>() {
            @Override public Object get(int index) { throw new AssertionError("elements must not be read"); }
            @Override public int size() { throw new AssertionError("size must not be read"); }
            @Override public java.util.Iterator<Object> iterator() { throw new AssertionError("iterator must not be requested"); }
            @Override public List<Object> reversed() { throw new AssertionError("reverse view must not be requested"); }
            @Override public String toString() { return "unread collection"; }
        };
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        for (var check : List.of(CollectionConditions.startsWith(List.of()), CollectionConditions.endsWith(List.of()),
                CollectionConditions.containsSequence(List.of()), CollectionConditions.containsSubsequence(List.of()))) {
            assertSame(actual, waiting.until(check));
        }
        for (var check : List.of(CollectionConditions.doesNotContainSequence(List.of()),
                CollectionConditions.doesNotContainSubsequence(List.of()))) {
            var failure = failure(diagnostic(waiting).until(check));
            assertEquals(TIMEOUT, failure.reason());
        }
    }

    @Test
    void orderedConditionsRejectIncompletePrefixesAndSuffixes() {
        for (List<Integer> actual : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.startsWith(List.of(1, 2, 3)), actual));
            assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.endsWith(List.of(1, 2, 3)), actual));
            assertInstanceOf(Unsatisfied.class, evaluate(CollectionConditions.containsSequence(List.of(1, 2, 3)), actual));
            assertSame(actual, ((Satisfied<?>) evaluate(CollectionConditions.doesNotContainSequence(List.of(1, 2, 3)), actual)).value());
        }
        var empty = new ArrayDeque<Integer>();
        assertSame(empty, ((Satisfied<?>) evaluate(CollectionConditions.sorted(Integer::compareTo), empty)).value());
    }

    @Test
    void orderedChecksPreserveSequentialAndSequencedContainersWithNullsAndArrays() {
        var elements = Arrays.asList(null, new int[] {1, 2}, "gap", new int[] {3, 4});
        List<SequencedCollection<Object>> containers = List.of(
                new LinkedList<>(elements), new LinkedHashSet<>(elements));
        for (var actual : containers) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time);

            assertSame(actual, waiting.until(CollectionConditions.startsWith(asList(null, new int[] {1, 2}))));
            assertSame(actual, waiting.until(CollectionConditions.endsWith(List.of("gap", new int[] {3, 4}))));
            assertSame(actual, waiting.until(CollectionConditions.containsSequence(List.of(new int[] {1, 2}, "gap"))));
            var captured = diagnostic(waiting).until(CollectionConditions.containsSubsequence(asList(null, new int[] {3, 4})));
            assertSame(actual, captured.orThrow());
        }
    }

    @Test
    void orderedExpectationsTreatReferenceArraysAsSingleElementsWithoutTypeArguments() {
        var expected = singletonList(new String[] {"a", "b"});
        var unrelated = singletonList(new String[] {"other"});
        var actual = singletonList(new String[] {"a", "b"});
        var waiting = await(() -> actual).usingTime(time, time);

        for (var check : List.of(CollectionConditions.startsWith(expected), CollectionConditions.endsWith(expected),
                CollectionConditions.containsSequence(expected), CollectionConditions.containsSubsequence(expected),
                CollectionConditions.doesNotContainSequence(unrelated), CollectionConditions.doesNotContainSubsequence(unrelated))) {
            var selected = waiting.until(check);
            AwaitResult<List<String[]>, List<String[]>> attempt = diagnostic(waiting).until(check);

            assertEquals("a", selected.getFirst()[0]);
            assertSame(actual, selected);
            assertSame(actual, attempt.orThrow());
        }
    }

    @Test
    void impossiblePatternLengthDoesNotReenterContainerCallbacks() {
        var expected = List.of(1, 2, 3);
        for (var elements : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            for (boolean negative : List.of(false, true)) {
                var conditions = negative
                        ? List.of(CollectionConditions.doesNotContainSequence(expected), CollectionConditions.doesNotContainSubsequence(expected))
                        : List.of(CollectionConditions.startsWith(expected), CollectionConditions.endsWith(expected),
                                CollectionConditions.containsSequence(expected), CollectionConditions.containsSubsequence(expected));
                for (var condition : conditions) {
                    var actual = new AbstractList<Integer>() {
                        private int sizeReads;

                        @Override
                        public int size() {
                            if (++sizeReads > 1) throw new AssertionError("an impossible pattern must not reenter size");
                            return elements.size();
                        }

                        @Override
                        public Integer get(int index) {
                            throw new AssertionError("an impossible pattern must not retrieve elements");
                        }
                    };
                    var evaluation = evaluate(condition, actual);
                    assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                    if (negative) assertSame(actual, ((Satisfied<?>) evaluation).value());
                }
            }
        }
    }

    @Test
    void nestedSelectorsWaitForANullCollectionToBecomeAvailable() {
        var observations = Arrays.asList(null, List.of(" "), null, List.of(" ")).iterator();
        var waiting = await(observations::next).usingTime(time, time)
                .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(3));

        String last = waiting.until(CollectionConditions.lastElement(String::isBlank));
        String singleElement = waiting.until(CollectionConditions.singleElement(matches(String::isBlank)));

        assertEquals(" ", last);
        assertEquals(" ", singleElement);
        assertFalse(observations.hasNext());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() {
        var missing = await(() -> (List<String>) null).usingTime(time, time).upTo(ofNanos(1));
        var values = await(() -> List.of("failed", "ready", "failed")).usingTime(time, time).upTo(ofNanos(1));

        assertFailure(TIMEOUT, () -> await(() -> List.of())
                .usingTime(time, time).upTo(ofNanos(1)).until(firstElement));
        assertFailure(TIMEOUT, () -> missing.until(lastElement));
        assertFailure(TIMEOUT, () -> missing.until(CollectionConditions.firstElement(value -> true)));
        assertFailure(TIMEOUT, () -> missing.until(CollectionConditions.elementAt(0)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.firstElement(value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.lastElement(value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.elementAt(4)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.elementAt(3)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.elementAt(0, value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> await(() -> List.of(1, 2))
                .usingTime(time, time).upTo(ofNanos(1)).until(singleElement(instanceOf(String.class))));
        assertFailure(TIMEOUT, () -> await(() -> List.of("first", "second"))
                .usingTime(time, time).upTo(ofNanos(1)).until(singleElement(instanceOf(String.class))));
        assertFailure(TIMEOUT, () -> await(() -> List.<Object>of(1, "ready"))
                .usingTime(time, time).upTo(ofNanos(1)).until(singleElement(instanceOf(String.class))));
        assertEquals("index must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConditions.elementAt(-1)).getMessage());
        assertThrows(NullPointerException.class, () -> CollectionConditions.sorted(null));
    }

    @Test
    void lastReturnsTheFinalPersistenceObservation() {
        var observations = new ArrayDeque<>(List.of(List.of(1), List.of(2), List.of(3)));
        java.util.concurrent.Callable<List<Integer>> source = observations::removeFirst;

        assertEquals(3, await(source::call).usingTime(time, time).every(ofNanos(1))
                .upTo(ofNanos(5)).persisting(ofNanos(2)).until(lastElement));
    }

    @Test
    void firstRetainsSelectedTimeoutDiagnostics() {
        java.util.concurrent.Callable<List<String>> source = List::of;

        var failure = assertFailure(TIMEOUT,
                () -> await(source::call).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                        .until(firstElement));

        assertThat(failure).hasMessageContainingAll(
                "Condition: collection has a first element", "Mismatch: collection size was 0");
    }

    private record User(int id) {
    }
}
