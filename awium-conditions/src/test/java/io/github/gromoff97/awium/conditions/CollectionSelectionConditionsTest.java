package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.CollectionConditions.any;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsNull;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsOnly;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsOnlyNulls;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsSequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsSubsequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainNull;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainSequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainSubsequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.elementAt;
import static io.github.gromoff97.awium.conditions.CollectionConditions.endsWith;
import static io.github.gromoff97.awium.conditions.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasNoDuplicates;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasSizeBetween;
import static io.github.gromoff97.awium.conditions.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.none;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.sorted;
import static io.github.gromoff97.awium.conditions.CollectionConditions.startsWith;
import static io.github.gromoff97.awium.conditions.CollectionConditions.subsetOf;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.result;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class CollectionSelectionConditionsTest {

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var users = new ArrayList<>(List.of(new User(2)));
        Callable<ArrayList<User>> source = () -> users;

        User matching = await(source::call).until(singleElement(matches(user -> user.id() == 2)));
        String typed = await((Callable<List<Object>>) () -> List.of("text")).until(singleElement(instanceOf(String.class)));
        String nullable = await(() -> asList((String) null)).until(singleElement);

        assertSame(users.getFirst(), matching);
        assertEquals("text", typed);
        assertNull(nullable);
        assertNull(await(() -> singletonList((String) null)).until(singleElement(matches(value -> value == null))));
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var actual = new ArrayList<>(List.of(2, 4, 6));
        Callable<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source::call).until(all(value -> value % 2 == 0)));
        assertSame(actual, await(source::call).until(any(value -> value == 4)));
        assertSame(actual, await(source::call).until(none(value -> value < 0)));
    }

    @Test
    void quantifiersCoverTheirUnsatisfiedBranches() {
        assertInstanceOf(Unsatisfied.class, evaluate(all(
                (Integer value) -> value % 2 == 0), List.of(2, 3)));
        assertInstanceOf(Unsatisfied.class, evaluate(any(
                (Integer value) -> value == 4), List.of(1, 3)));
        assertInstanceOf(Unsatisfied.class, evaluate(none(
                (Integer value) -> value < 0), List.of(1, -1)));
    }

    @Test
    void subsetPreservesTypedCollectionsAcrossTerminalsAndComposition() {
        var actual = new ArrayList<>(List.of("ready"));
        var plain = await(() -> actual).until(subsetOf(List.of("ready", "done")));
        var selected = await(() -> List.of(actual)).until(singleElement(subsetOf(List.of("ready"))));
        var captured = await(() -> actual)
                .until(captured(subsetOf(List.of("ready")), subsetOf(List.of("ready", "done"))));

        assertSame(actual, plain);
        assertSame(actual, selected);
        assertSame(actual, captured.getLast());
        assertEquals(List.of(5, 5, 5), List.of(plain.getFirst().length(),
                selected.getFirst().length(), captured.getLast().getFirst().length()));

        var diagnostic = tryAwait(() -> actual).until(subsetOf(List.of("ready")));
        switch (diagnostic) {
            case AwaitResult.Succeeded(var history, var value) -> {
                assertSame(actual, value);
                assertEquals(5, value.getFirst().length());
            }
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure);
        }

        List<CharSequence> text = List.of("ready");
        assertSame(text, await(() -> text).until(subsetOf(List.of("ready"))));
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() {
        var values = new ArrayList<>(asList("a", "a", null));

        assertInstanceOf(Satisfied.class, evaluate(containsNull, values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(doesNotContainNull, values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(hasNoDuplicates, values));
        assertInstanceOf(Satisfied.class,
                evaluate(containsOnly(asList("a", null)), values));
        assertInstanceOf(Satisfied.class, evaluate(subsetOf(
                asList("a", "b", null)), values));
        assertInstanceOf(Satisfied.class,
                evaluate(hasSizeBetween(2, 4), values));
        assertInstanceOf(Satisfied.class, evaluate(
                hasSize(3), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(containsOnlyNulls, List.of()));
        assertInstanceOf(Unsatisfied.class,
                evaluate(containsNull, List.of("a")));
        assertInstanceOf(Satisfied.class,
                evaluate(doesNotContainNull, List.of("a")));
        assertInstanceOf(Satisfied.class, evaluate(containsOnlyNulls,
                asList(null, null)));
        assertInstanceOf(Unsatisfied.class, evaluate(containsOnlyNulls,
                asList(null, "a")));
        assertInstanceOf(Satisfied.class, evaluate(hasNoDuplicates,
                List.of("a", "b")));
        assertInstanceOf(Unsatisfied.class, evaluate(containsOnly(asList("a", null)),
                asList("a", "b", null)));
        assertInstanceOf(Unsatisfied.class, evaluate(subsetOf(
                List.of("a", "b")), List.of("a", "c")));
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() {

        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        Callable<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source::call).until(firstElement));
        assertEquals(5, await(source::call).until(lastElement));
        assertEquals(3, await(source::call).until(elementAt(2)));
        assertEquals(1, await(source::call).until(firstElement(value -> value > 0)));
        assertEquals(5, await(source::call).until(lastElement(value -> value < 6)));
        assertSame(values, await(source::call).until(startsWith(List.of(1, 2))));
        assertSame(values, await(source::call).until(endsWith(List.of(3, 5))));
        var pair = List.of(1, 2);
        var exact = await(() -> pair);
        assertSame(pair, exact.until(startsWith(List.of(1, 2))));
        assertSame(pair, exact.until(endsWith(List.of(1, 2))));
        assertSame(values, await(source::call).until(containsSequence(List.of(2, 3))));
        assertInstanceOf(Satisfied.class, evaluate(containsSequence(List.of(1, 2)),
                List.of(1, 2)));
        assertSame(values, await(source::call).until(containsSubsequence(List.of(1, 3, 5))));
        assertSame(values, await(source::call).until(sorted(Integer::compareTo)));

        assertInstanceOf(Unsatisfied.class,
                evaluate(startsWith(List.of(1, 3)), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(endsWith(List.of(2, 5)), values));
        assertInstanceOf(Unsatisfied.class,
                evaluate(containsSequence(List.of(1, 3)), values));
        assertInstanceOf(Satisfied.class, evaluate(
                doesNotContainSequence(List.of(1, 3)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                doesNotContainSequence(List.of(2, 3)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                containsSubsequence(List.of(1, 4)), values));
        assertInstanceOf(Satisfied.class, evaluate(
                doesNotContainSubsequence(List.of(1, 4)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(
                doesNotContainSubsequence(List.of(1, 3, 5)), values));
        assertInstanceOf(Unsatisfied.class, evaluate(sorted(Integer::compareTo),
                List.of(1, 3, 2)));
        var equalElements = List.of(1, 1);
        assertSame(equalElements, await(() -> equalElements)
                .until(sorted(Integer::compareTo)));
        assertInstanceOf(Satisfied.class, evaluate(sorted(
                comparingInt(Integer::intValue).reversed()), List.of(3, 2, 1)));

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await(() -> sequencedSet).until(firstElement));
        assertSame(sequencedSet, await(() -> sequencedSet).until(
                startsWith(List.of(1, 2))));
    }

    @Test
    void singleElementRequiresOneElementBeforeApplyingTheCheck() {


        var none = rejection(singleElement(matches(value -> value > 10)), List.of(1));
        var many = rejection(singleElement(matches(value -> value == 2)), List.of(1, 2));

        assertThat(none.mismatch()).contains("value did not match");
        assertThat(many.mismatch()).contains("collection size was greater than 1");
    }

    @Test
    void emptyOrderedPatternsMatchEmptyAndNonEmptyCollections() {
        List<AwaitCondition.PreservingCondition<SequencedCollection<? super String>>> positive = List.of(
                startsWith(List.of()), endsWith(List.of()),
                containsSequence(List.of()), containsSubsequence(List.of()));
        List<AwaitCondition.PreservingCondition<SequencedCollection<? super String>>> negative = List.of(
                doesNotContainSequence(List.of()), doesNotContainSubsequence(List.of()));
        for (var checks : List.of(positive, negative)) {
            for (var check : checks) assertInstanceOf(Unsatisfied.class, evaluate(check, null));
        }

        for (var values : List.of(List.<String>of(), List.of("a", "b"))) {
            var actual = new ArrayList<>(values);

            var waiting = await(() -> actual).every(ofNanos(1));
            for (var check : positive) {
                ArrayList<String> selected = waiting.until(check);
                assertSame(actual, selected);
                assertSame(actual, tryAwait(() -> actual).every(ofNanos(1)).until(check).orThrow());
            }
            for (var check : negative) {
                var explained = check.because("pattern must be absent");
                rejection(explained, actual);
                assertEquals("pattern must be absent", explained.explanation());
                assertEquals(List.of(), explained.references().getFirst().value());
            }
        }
    }

    @Test
    void emptyOrderedPatternsDoNotReadTheObservedCollection() {
        var actual = new AbstractList<Object>() {
            @Override public Object get(int index) { throw new AssertionError("elements must not be read"); }
            @Override public int size() { throw new AssertionError("size must not be read"); }
            @Override public Iterator<Object> iterator() { throw new AssertionError("iterator must not be requested"); }
            @Override public List<Object> reversed() { throw new AssertionError("reverse view must not be requested"); }
            @Override public String toString() { return "unread collection"; }
        };
        var waiting = await(() -> actual).every(ofNanos(1));
        for (var check : List.of(startsWith(List.of()), endsWith(List.of()),
                containsSequence(List.of()), containsSubsequence(List.of()))) {
            assertSame(actual, waiting.until(check));
        }
        for (var check : List.of(doesNotContainSequence(List.of()),
                doesNotContainSubsequence(List.of()))) {
            var failure = rejection(check, actual);
        }
    }

    @Test
    void orderedConditionsRejectIncompletePrefixesAndSuffixes() {
        for (List<Integer> actual : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            assertInstanceOf(Unsatisfied.class, evaluate(startsWith(List.of(1, 2, 3)), actual));
            assertInstanceOf(Unsatisfied.class, evaluate(endsWith(List.of(1, 2, 3)), actual));
            assertInstanceOf(Unsatisfied.class, evaluate(containsSequence(List.of(1, 2, 3)), actual));
            assertSame(actual, ((Satisfied<?>) evaluate(doesNotContainSequence(List.of(1, 2, 3)), actual)).value());
        }
        var empty = new ArrayDeque<Integer>();
        assertSame(empty, ((Satisfied<?>) evaluate(sorted(Integer::compareTo), empty)).value());
    }

    @Test
    void orderedChecksPreserveSequentialAndSequencedContainersWithNullsAndArrays() {
        var elements = asList(null, new int[] {1, 2}, "gap", new int[] {3, 4});
        List<SequencedCollection<Object>> containers = List.of(
                new LinkedList<>(elements), new LinkedHashSet<>(elements));
        for (var actual : containers) {

            var waiting = await(() -> actual);

            assertSame(actual, waiting.until(startsWith(asList(null, new int[] {1, 2}))));
            assertSame(actual, waiting.until(endsWith(List.of("gap", new int[] {3, 4}))));
            assertSame(actual, waiting.until(containsSequence(List.of(new int[] {1, 2}, "gap"))));
            var captured = tryAwait(() -> actual).until(containsSubsequence(asList(null, new int[] {3, 4})));
            assertSame(actual, captured.orThrow());
        }
    }

    @Test
    void orderedExpectationsTreatReferenceArraysAsSingleElementsWithoutTypeArguments() {
        var expected = singletonList(new String[] {"a", "b"});
        var unrelated = singletonList(new String[] {"other"});
        var actual = singletonList(new String[] {"a", "b"});
        var waiting = await(() -> actual);

        for (var check : List.of(startsWith(expected), endsWith(expected),
                containsSequence(expected), containsSubsequence(expected),
                doesNotContainSequence(unrelated), doesNotContainSubsequence(unrelated))) {
            var selected = waiting.until(check);
            AwaitResult<List<String[]>, List<String[]>> attempt = tryAwait(() -> actual).until(check);

            assertEquals("a", selected.getFirst()[0]);
            assertSame(actual, selected);
            assertSame(actual, attempt.orThrow());
        }
    }

    @TestFactory
    Stream<DynamicTest> impossiblePositivePatternsRejectWithoutReadingElements() {
        var expected = List.of(1, 2, 3);
        return Stream.of(startsWith(expected), endsWith(expected), containsSequence(expected), containsSubsequence(expected))
                .flatMap(condition -> range(0, 3).mapToObj(size -> dynamicTest(condition.description() + " " + condition.references() + " / size: " + size,
                        () -> assertInstanceOf(Unsatisfied.class, evaluate(condition, tooShortList(size))))));
    }

    @TestFactory
    Stream<DynamicTest> impossibleNegativePatternsPreserveTheCollectionWithoutReadingElements() {
        var expected = List.of(1, 2, 3);
        return Stream.of(doesNotContainSequence(expected), doesNotContainSubsequence(expected))
                .flatMap(condition -> range(0, 3).mapToObj(size -> dynamicTest(condition.description() + " " + condition.references() + " / size: " + size, () -> {
                    var actual = tooShortList(size);
                    assertSame(actual, result(evaluate(condition, actual)));
                })));
    }

    private static List<Integer> tooShortList(int size) {
        return new AbstractList<>() {
            private int sizeReads;

            @Override public int size() {
                assertEquals(1, ++sizeReads, "an impossible pattern must not reenter size");
                return size;
            }

            @Override public Integer get(int index) {
                throw new AssertionError("an impossible pattern must not retrieve elements");
            }
        };
    }

    @Test
    void nestedSelectorsWaitForANullCollectionToBecomeAvailable() {
        var observations = asList(null, List.of(" "), null, List.of(" ")).iterator();
        var waiting = await(observations::next)
                .every(ofNanos(1));

        String last = waiting.until(lastElement(String::isBlank));
        String singleElement = waiting.until(singleElement(matches(String::isBlank)));

        assertEquals(" ", last);
        assertEquals(" ", singleElement);
        assertFalse(observations.hasNext());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() {



        rejection(firstElement, List.of());
        rejection(lastElement, (List<String>) null);
        rejection(firstElement(value -> true), (List<String>) null);
        rejection(elementAt(0), (List<String>) null);
        rejection(firstElement(value -> value.startsWith("r")), List.of("failed", "ready", "failed"));
        rejection(lastElement(value -> value.startsWith("r")), List.of("failed", "ready", "failed"));
        rejection(elementAt(4), List.of("failed", "ready", "failed"));
        rejection(elementAt(3), List.of("failed", "ready", "failed"));
        rejection(elementAt(0, value -> value.startsWith("r")), List.of("failed", "ready", "failed"));
        rejection(singleElement(instanceOf(String.class)), List.of(1, 2));
        rejection(singleElement(instanceOf(String.class)), List.of("first", "second"));
        rejection(singleElement(instanceOf(String.class)), List.<Object>of(1, "ready"));
        assertEquals("index must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> elementAt(-1)).getMessage());
        assertThrows(NullPointerException.class, () -> sorted(null));
    }

    @Test
    void lastReturnsTheLatestObservationWhenItsExecutionIsReused() throws Throwable {
        var execution = ConditionExecution.<List<Integer>, Integer>bind(lastElement);
        for (int last : List.of(1, 2, 3)) assertEquals(last, result(execution.probe(List.of(last))));
    }

    @Test
    void firstExplainsAnEmptyCollection() {
        Callable<List<String>> source = List::of;

        var failure = rejection(firstElement, assertDoesNotThrow(source::call));

        assertEquals("collection has a first element", firstElement.description());
        assertEquals("collection size was 0", failure.mismatch());
    }

    private record User(int id) {
    }
}
