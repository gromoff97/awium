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
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
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

@SuppressWarnings("varargs")
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
                nested::preservingAttempt));
    }

    public static <ELEMENT, RESULT> Condition<Collection<? extends ELEMENT>, RESULT> single(Condition<? super ELEMENT, ? extends RESULT> nested) {
        return ConditionSupport.single("collection", required(nested), actual -> actual,
                nested::newAttempt);
    }

    public static <EXPECTED> SelectedCondition<Collection<? super EXPECTED>> single(ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.single("collection", required(nested).definition, actual -> actual,
                nested.definition::preservingAttempt));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate));
    }

    /** Matches one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(ELEMENT expected) {
        return containsAll(singletonList(expected));
    }

    /** Matches every element in a nonempty shallow snapshot of the expected collection. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAll(Collection<? extends ELEMENT> expected) {
        var values = nonEmpty(snapshot(expected, "expected elements"), "expected elements");
        return preserving("collection contains all expected elements", "collection did not contain all expected elements", expectedReference(values),
                actual -> ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    /** Rejects one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(ELEMENT unexpected) {
        return doesNotContainAny(singletonList(unexpected));
    }

    /** Rejects every element in a nonempty shallow snapshot of the unexpected collection. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAny(Collection<? extends ELEMENT> unexpected) {
        var values = nonEmpty(snapshot(unexpected, "unexpected elements"), "unexpected elements");
        return preserving("collection does not contain any unexpected elements", "collection contained an unexpected element", unexpectedReference(values),
                actual -> !matchesAny(actual,
                        value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(Collection<? extends ELEMENT> unexpected) {
        var values = nonEmpty(snapshot(unexpected, "unexpected elements"), "unexpected elements");
        return preserving("collection does not contain all unexpected elements", "collection contained all unexpected elements", unexpectedReference(values),
                actual -> !ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(Collection<? extends ELEMENT> expected) {
        var values = nonEmpty(snapshot(expected, "expected elements"), "expected elements");
        return preserving("collection contains an expected element", "collection did not contain an expected element", expectedReference(values),
                actual -> matchesAny(actual,
                        value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements", expectedReference(values),
                actual -> exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements",
                "collection contained exactly the expected elements", expectedReference(values),
                actual -> !exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order", expectedReference(values),
                actual -> exactContentInAnyOrder(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order", expectedReference(values),
                actual -> !exactContentInAnyOrder(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsOnly(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains only the expected elements",
                "collection did not contain only the expected elements", expectedReference(values),
                actual -> sameDistinctElements(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> subsetOf(Collection<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection is a subset of the expected elements",
                "collection was not a subset of the expected elements", expectedReference(values),
                actual -> matchesAll(actual, value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> startsWith(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected prefix", "collection did not contain expected prefix", expectedReference(values),
                actual -> actual.size() >= values.size() && prefixMatches(actual.iterator(), values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> endsWith(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected suffix", "collection did not contain expected suffix", expectedReference(values),
                actual -> actual.size() >= values.size() && prefixMatches(actual.reversed().iterator(), values.reversed()));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected sequence", "collection did not contain expected sequence", expectedReference(values),
                actual -> containsSequence(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection does not contain expected sequence", "collection contained expected sequence", expectedReference(values),
                actual -> !containsSequence(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSubsequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence", expectedReference(values),
                actual -> containsSubsequence(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSubsequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = nonEmptySnapshot(expected, "expected elements");
        return preserving("collection does not contain expected subsequence",
                "collection contained expected subsequence", expectedReference(values),
                actual -> !containsSubsequence(actual, values));
    }

    public static final SelectedCondition<SequencedCollection<?>> first = selected("collection has a first element",
            actual -> selectFirst(actual, value -> true));

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> first(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("collection has a matching element", actual -> selectFirst(actual, predicate));
    }

    public static final SelectedCondition<SequencedCollection<?>> last = selected("collection has a last element",
            actual -> selectFirst(actual == null ? null : actual.reversed(), value -> true));

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> last(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("collection has a last matching element",
                actual -> selectFirst(actual == null ? null : actual.reversed(), predicate));
    }

    public static SelectedCondition<List<?>> element(int index) {
        return element(index, value -> true);
    }

    public static <ELEMENT> SelectedCondition<List<? extends ELEMENT>> element(int index, Predicate<? super ELEMENT> predicate) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        requireNonNull(predicate, "predicate must not be null");
        return selected("list has a matching element at index " + index, actual -> {
            if (actual == null) {
                return unsatisfied("list was null");
            }
            int position = 0;
            for (ELEMENT value : actual) {
                if (position++ == index) {
                    return predicate.test(value) ? satisfied(value) : unsatisfied("element at index " + index + " did not match");
                }
            }
            return unsatisfied("list size was " + position);
        });
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? extends ELEMENT>> sorted(Comparator<? super ELEMENT> comparator) {
        requireNonNull(comparator, "comparator must not be null");
        return preserving("collection is sorted", "collection was not sorted", actual -> isSorted(actual, comparator));
    }

    private static <ELEMENT> ConditionResult<ELEMENT> selectFirst(Iterable<? extends ELEMENT> actual,
            Predicate<? super ELEMENT> predicate) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        for (ELEMENT value : actual) {
            if (predicate.test(value)) {
                return satisfied(value);
            }
        }
        return unsatisfied("no collection element matched");
    }

    private static PreservingCondition<Collection<?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("collection", "size", bound, matches, description, Collection::size);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            Predicate<? super VALUES> matches) {
        return preservingNonNull("collection", description, mismatch, matches);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            Reference<?> reference, Predicate<? super VALUES> matches) {
        return preservingNonNull("collection", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> values = actual.iterator();
        return prefixMatches(values, expected) && !values.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) {
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

    private static boolean hasUniqueElements(Collection<?> actual) {
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
