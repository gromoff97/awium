package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmptySnapshot;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static java.util.Objects.deepEquals;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/** Collection checks and selections; predicate quantifiers also accept non-collection iterables. */
public final class CollectionConditions {

    public static final SelectedCondition<Collection<?>> single = selected("collection has a single element",
            actual -> ConditionSupport.single("collection", actual, ConditionResult::satisfied));
    public static final PreservingCondition<Collection<?>> empty = preserving("collection is empty", "collection was not empty", Collection::isEmpty);
    public static final PreservingCondition<Collection<?>> nonEmpty = preserving("collection is not empty", "collection was empty", actual -> !actual.isEmpty());
    public static final PreservingCondition<Collection<?>> containsNull = preserving("collection contains null",
            "collection did not contain null", actual -> matchesAny(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> doesNotContainNull = preserving("collection does not contain null",
            "collection contained null", actual -> !matchesAny(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> containsOnlyNulls = preserving("collection contains only nulls",
            "collection did not contain only nulls",
            actual -> !actual.isEmpty() && matchesAll(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> hasNoDuplicates = preserving("collection has no duplicates",
            "collection contained duplicates", CollectionConditions::hasUniqueElements);

    private CollectionConditions() {}

    public static PreservingCondition<Collection<?>> size(int expected) {
        return sized(expected, actual -> actual == expected,
                "collection size is " + expected);
    }

    public static PreservingCondition<Collection<?>> sizeIsNot(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected,
                "collection size is not " + unexpected);
    }

    public static PreservingCondition<Collection<?>> sizeGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound,
                "collection size is greater than " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> sizeAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound,
                "collection size is at least " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> sizeLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound,
                "collection size is less than " + upperBound);
    }

    public static PreservingCondition<Collection<?>> sizeAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound,
                "collection size is at most " + upperBound);
    }

    public static PreservingCondition<Collection<?>> sizeBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "size");
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "collection size is between " + lowerBound + " and " + upperBound);
    }

    public static <ELEMENT> SelectedCondition<Collection<? extends ELEMENT>> single(PreservingCondition<? super ELEMENT> nested) {
        return new SelectedCondition<>(ConditionSupport.single("collection", required(nested).definition, actual -> actual,
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /** Selects a unique matching result from an iterable using a condition with a declared result type. */
    public static <ELEMENT, RESULT> Condition<Iterable<? extends ELEMENT>, RESULT> single(Condition<? super ELEMENT, RESULT> nested) {
        return ConditionSupport.single("collection", required(nested), actual -> actual,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <EXPECTED> SelectedCondition<Collection<? super EXPECTED>> single(ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.single("collection", required(nested).definition, actual -> actual,
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    public static <ELEMENT> PreservingCondition<Iterable<? extends ELEMENT>> all(CheckedPredicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate));
    }

    public static <ELEMENT> PreservingCondition<Iterable<? extends ELEMENT>> any(CheckedPredicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate));
    }

    public static <ELEMENT> PreservingCondition<Iterable<? extends ELEMENT>> none(CheckedPredicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate));
    }

    /** Matches one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(ELEMENT expected) {
        return containsAll(singletonList(expected));
    }

    /** Matches every element in a nonempty shallow snapshot of the expected iterable. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAll(Iterable<? extends ELEMENT> expected) {
        var values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains all expected elements", "collection did not contain all expected elements", expectedReference(values),
                actual -> ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    /** Rejects one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(ELEMENT unexpected) {
        return doesNotContainAny(singletonList(unexpected));
    }

    /** Rejects every element in a nonempty shallow snapshot of the unexpected iterable. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAny(Iterable<? extends ELEMENT> unexpected) {
        var values = nonEmptySnapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain any unexpected elements", "collection contained an unexpected element", unexpectedReference(values),
                actual -> !ValueMatching.containsAny(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(Iterable<? extends ELEMENT> unexpected) {
        var values = nonEmptySnapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain all unexpected elements", "collection contained all unexpected elements", unexpectedReference(values),
                actual -> !ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(Iterable<? extends ELEMENT> expected) {
        var values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains an expected element", "collection did not contain an expected element", expectedReference(values),
                actual -> ValueMatching.containsAny(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements", expectedReference(values),
                actual -> exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements",
                "collection contained exactly the expected elements", expectedReference(values),
                actual -> !exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order", expectedReference(values),
                actual -> exactContentInAnyOrder(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order", expectedReference(values),
                actual -> !exactContentInAnyOrder(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsOnly(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains only the expected elements",
                "collection did not contain only the expected elements", expectedReference(values),
                actual -> sameDistinctElements(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> subsetOf(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection is a subset of the expected elements",
                "collection was not a subset of the expected elements", expectedReference(values),
                actual -> matchesAll(actual, value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> startsWith(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected prefix", "collection did not contain expected prefix", expectedReference(values),
                actual -> actual.size() >= values.size() && prefixMatches(actual.iterator(), values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> endsWith(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected suffix", "collection did not contain expected suffix", expectedReference(values),
                actual -> actual.size() >= values.size() && prefixMatches(actual.reversed().iterator(), values.reversed()));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected sequence", "collection did not contain expected sequence", expectedReference(values),
                actual -> containsSequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection does not contain expected sequence", "collection contained expected sequence", expectedReference(values),
                actual -> !containsSequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSubsequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence", expectedReference(values),
                actual -> containsSubsequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSubsequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection does not contain expected subsequence",
                "collection contained expected subsequence", expectedReference(values),
                actual -> !containsSubsequence(actual, values));
    }

    public static final SelectedCondition<SequencedCollection<?>> first = selected("collection has a first element",
            CollectionConditions::selectFirst);

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> first(CheckedPredicate<? super ELEMENT> predicate) {
        return first(Conditions.matches(predicate));
    }

    /** Selects the first matching element, retaining the source's element type. */
    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> first(PreservingCondition<? super ELEMENT> nested) {
        return new SelectedCondition<>(matching("collection has a first matching value: ", required(nested).definition,
                actual -> actual, () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /** Selects the first compatible comparison match, retaining the source's element type. */
    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> first(ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(matching("collection has a first matching value: ", required(nested).definition,
                actual -> actual, () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /**
     * Selects the first satisfied nested result in encounter order.
     *
     * @param <ELEMENT> input type accepted by the nested condition
     * @param <RESULT> result produced by the nested condition
     * @param nested condition applied to each visited element
     * @return an immutable condition with the nested result type
     */
    public static <ELEMENT, RESULT> Condition<SequencedCollection<? extends ELEMENT>, RESULT> first(
            Condition<? super ELEMENT, RESULT> nested) {
        return matching("collection has a first matching value: ", required(nested), actual -> actual,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static final SelectedCondition<SequencedCollection<?>> last = selected("collection has a last element",
            actual -> selectFirst(actual == null ? null : actual.reversed()));

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> last(CheckedPredicate<? super ELEMENT> predicate) {
        return last(Conditions.matches(predicate));
    }

    /** Selects the last matching element, retaining the source's element type. */
    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> last(PreservingCondition<? super ELEMENT> nested) {
        return new SelectedCondition<>(matching("collection has a last matching value: ", required(nested).definition,
                SequencedCollection::reversed, () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /** Selects the last compatible comparison match, retaining the source's element type. */
    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> last(ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(matching("collection has a last matching value: ", required(nested).definition,
                SequencedCollection::reversed, () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /**
     * Selects the first satisfied nested result in reverse encounter order.
     *
     * @param <ELEMENT> input type accepted by the nested condition
     * @param <RESULT> result produced by the nested condition
     * @param nested condition applied to each visited element
     * @return an immutable condition with the nested result type
     */
    public static <ELEMENT, RESULT> Condition<SequencedCollection<? extends ELEMENT>, RESULT> last(
            Condition<? super ELEMENT, RESULT> nested) {
        return matching("collection has a last matching value: ", required(nested), SequencedCollection::reversed,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static SelectedCondition<List<?>> element(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        return selected("list has a matching element at index " + index,
                actual -> selectElement(actual, index));
    }

    public static <ELEMENT> SelectedCondition<List<? extends ELEMENT>> element(int index, CheckedPredicate<? super ELEMENT> predicate) {
        return element(index, Conditions.matches(predicate));
    }

    /** Checks the indexed element, retaining the source's element type. */
    public static <ELEMENT> SelectedCondition<List<? extends ELEMENT>> element(int index, PreservingCondition<? super ELEMENT> nested) {
        var extraction = element(index);
        return new SelectedCondition<>(ConditionSupport.compose("element at index " + index + " matches: ", required(nested).definition,
                extraction.definition, actual -> selectElement(actual, index),
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /** Compares the indexed element, retaining the source's element type. */
    public static <EXPECTED> SelectedCondition<List<? super EXPECTED>> element(int index, ComparisonCondition<EXPECTED> nested) {
        var extraction = element(index);
        return new SelectedCondition<>(ConditionSupport.compose("element at index " + index + " matches: ", required(nested).definition,
                extraction.definition, actual -> selectElement(actual, index),
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    /**
     * Evaluates the indexed value once per attempt and returns the nested result.
     *
     * @param <ELEMENT> input type accepted by the nested condition
     * @param <RESULT> result produced by the nested condition
     * @param index non-negative element index
     * @param nested condition applied to the indexed value
     * @return an immutable condition with the nested result type
     */
    public static <ELEMENT, RESULT> Condition<List<? extends ELEMENT>, RESULT> element(int index,
            Condition<? super ELEMENT, RESULT> nested) {
        var extraction = element(index);
        return ConditionSupport.compose("element at index " + index + " matches: ", required(nested), extraction.definition,
                actual -> selectElement(actual, index), () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? extends ELEMENT>> sorted(Comparator<? super ELEMENT> comparator) {
        requireNonNull(comparator, "comparator must not be null");
        return preserving("collection is sorted", "collection was not sorted", actual -> isSorted(actual, comparator));
    }

    private static <ELEMENT> ConditionResult<ELEMENT> selectFirst(Iterable<? extends ELEMENT> actual) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        var iterator = actual.iterator();
        return iterator.hasNext() ? satisfied(iterator.next()) : unsatisfied("no collection element matched");
    }

    private static <OBSERVED, ELEMENT, RESULT> Condition<OBSERVED, RESULT> matching(
            String description, Condition<?, ?> nested,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> order,
            Supplier<? extends ConditionAwaitAttempt<? super ELEMENT, ? extends RESULT>> evaluatorFactory) {
        return new Condition<>(description + nested.description(), nested, () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? AttemptResult.of(unsatisfied("collection was null"))
                    : selectFirst(order.apply(actual), evaluator);
        });
    }

    private static <ELEMENT, RESULT> AttemptResult<? extends RESULT> selectFirst(Iterable<? extends ELEMENT> values,
            ConditionAwaitAttempt<? super ELEMENT, ? extends RESULT> evaluator) {
        AttemptResult<? extends RESULT> result = AttemptResult.of(unsatisfied("no value matched"));
        for (ELEMENT value : values) {
            switch (evaluator.probe(value)) {
                case AttemptResult.Uncontrolled<? extends RESULT> failure -> { return failure; }
                case AttemptResult.Evaluated<? extends RESULT> evaluated -> {
                    if (evaluated.result() instanceof ConditionResult.Satisfied<?>) return evaluated;
                    result = evaluated;
                }
            }
        }
        return result;
    }

    private static <ELEMENT> ConditionResult<ELEMENT> selectElement(List<? extends ELEMENT> actual, int index) {
        if (actual == null) return unsatisfied("list was null");
        int position = 0;
        for (ELEMENT value : actual) {
            if (position++ == index) {
                return satisfied(value);
            }
        }
        return unsatisfied("list size was " + position);
    }

    private static PreservingCondition<Collection<?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("collection", "size", bound, matches, description, Collection::size);
    }

    private static <VALUES extends Iterable<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            CheckedPredicate<? super VALUES> matches) {
        return preservingNonNull("collection", description, mismatch, matches);
    }

    private static <VALUES extends Iterable<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            Reference<?> reference, CheckedPredicate<? super VALUES> matches) {
        return preservingNonNull("collection", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> values = actual.iterator();
        return prefixMatches(values, expected) && !values.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) throws Throwable {
        return actual.size() == expected.size()
                && exactly(actual.iterator(), expected, Objects::deepEquals);
    }

    private static boolean containsSequence(SequencedCollection<?> actual, List<?> expected) {
        if (actual.size() < expected.size()) return false;
        var window = new LinkedList<Object>();
        for (Object value : actual) {
            window.addLast(value);
            if (window.size() < expected.size()) continue;
            if (prefixMatches(window.iterator(), expected)) return true;
            window.removeFirst();
        }
        return false;
    }

    private static boolean containsSubsequence(Iterable<?> actual, List<?> expected) {
        int expectedIndex = 0;
        for (Object value : actual) {
            if (deepEquals(value, expected.get(expectedIndex)) && ++expectedIndex == expected.size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean prefixMatches(Iterator<?> actual, Iterable<?> expected) {
        for (Object value : expected) {
            if (!actual.hasNext() || !deepEquals(actual.next(), value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUniqueElements(Collection<?> actual) throws Throwable {
        List<Object> seen = new ArrayList<>();
        for (Object value : actual) {
            if (matchesAny(seen, previous -> deepEquals(previous, value))) {
                return false;
            }
            seen.add(value);
        }
        return true;
    }

    private static <ELEMENT> boolean isSorted(SequencedCollection<ELEMENT> actual, Comparator<? super ELEMENT> comparator) {
        Iterator<ELEMENT> values = actual.iterator();
        if (!values.hasNext()) {
            return true;
        }
        ELEMENT previous = values.next();
        while (values.hasNext()) {
            ELEMENT next = values.next();
            if (comparator.compare(previous, next) > 0) {
                return false;
            }
            previous = next;
        }
        return true;
    }

}
