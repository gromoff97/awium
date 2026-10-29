package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionSupport.Quantifier;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.internal.AttemptResult.satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.quantified;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

/**
 * Checks and selections for {@link Collection} sources. Checks preserve the observed collection's
 * type and instance; selections return an element or the nested condition's result.
 * A null observed collection is unsatisfied, including for absence checks.
 *
 * <p>Ordered operations, including indexed selection, require {@link SequencedCollection}.
 * Expected sets and sequences may come from any {@link Iterable} and are copied when the condition
 * is created, without copying their elements. A single expected element remains caller-owned.
 */
public final class CollectionConditions {

    public static final SelectedCondition<Collection<?>> singleElement = selected("collection has a single element",
            actual -> ConditionSupport.single("collection", actual));
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

    public static PreservingCondition<Collection<?>> hasSize(int expected) {
        return sized(expected, actual -> actual == expected, "is " + expected);
    }

    public static PreservingCondition<Collection<?>> hasSizeNotEqualTo(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected, "is not " + unexpected);
    }

    public static PreservingCondition<Collection<?>> hasSizeGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound, "is less than " + upperBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound, "is at most " + upperBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "size");
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "is between " + lowerBound + " and " + upperBound);
    }

    /** Checks the sole element, retaining the source's element type. */
    public static <ELEMENT> SelectedCondition<Collection<? extends ELEMENT>> singleElement(CheckedPredicate<? super ELEMENT> predicate) {
        return singleElement(Conditions.matches(predicate));
    }

    public static <CHECKED> SelectedCondition<Collection<? extends CHECKED>> singleElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("single element", nested, singleElement));
    }

    public static <EXPECTED> SelectedCondition<Collection<? super EXPECTED>> singleElement(
            Condition.ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("single element", nested, singleElement));
    }

    /** Checks the sole element using a condition with a declared result type. */
    public static <ELEMENT, RESULT> Condition.Typed<Collection<? extends ELEMENT>, RESULT> singleElement(Condition.Typed<? super ELEMENT, RESULT> nested) {
        return ConditionSupport.compose("single element", nested, singleElement);
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(CheckedPredicate<? super ELEMENT> predicate) {
        return all(Conditions.matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(Condition.InputCondition<? super ELEMENT> nested) {
        return quantified("collection", "elements", Quantifier.ALL, nested, actual -> actual);
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> all(Condition.ComparisonCondition<EXPECTED> nested) {
        return quantified("collection", "elements", Quantifier.ALL, nested, actual -> actual);
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(CheckedPredicate<? super ELEMENT> predicate) {
        return any(Conditions.matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(Condition.InputCondition<? super ELEMENT> nested) {
        return quantified("collection", "elements", Quantifier.ANY, nested, actual -> actual);
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> any(Condition.ComparisonCondition<EXPECTED> nested) {
        return quantified("collection", "elements", Quantifier.ANY, nested, actual -> actual);
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(CheckedPredicate<? super ELEMENT> predicate) {
        return none(Conditions.matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(Condition.InputCondition<? super ELEMENT> nested) {
        return quantified("collection", "elements", Quantifier.NONE, nested, actual -> actual);
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> none(Condition.ComparisonCondition<EXPECTED> nested) {
        return quantified("collection", "elements", Quantifier.NONE, nested, actual -> actual);
    }

    /** Matches one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(ELEMENT expected) {
        return preserving("collection contains expected element", "collection did not contain expected element", expectedReference(expected),
                actual -> matchesAny(actual, value -> deepEquals(value, expected)));
    }

    /** Matches every expected element; an empty expected iterable matches any non-null collection. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAll(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains all expected elements", "collection did not contain all expected elements", expectedReference(values),
                actual -> ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    /** Rejects one element, including a collection or array as that single element. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(ELEMENT unexpected) {
        return preserving("collection does not contain unexpected element", "collection contained unexpected element", unexpectedReference(unexpected),
                actual -> !matchesAny(actual, value -> deepEquals(value, unexpected)));
    }

    /** Rejects every unexpected element; an empty unexpected iterable matches any non-null collection. */
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAny(Iterable<? extends ELEMENT> unexpected) {
        var values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain any unexpected elements", "collection contained an unexpected element", unexpectedReference(values),
                actual -> !ValueMatching.containsAny(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(Iterable<? extends ELEMENT> unexpected) {
        var values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain all unexpected elements", "collection contained all unexpected elements", unexpectedReference(values),
                actual -> !ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains an expected element", "collection did not contain an expected element", expectedReference(values),
                actual -> ValueMatching.containsAny(actual, values, Objects::deepEquals));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements", expectedReference(values),
                actual -> exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(Iterable<? extends ELEMENT> unexpected) {
        var values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain exactly the unexpected elements",
                "collection contained exactly the unexpected elements", unexpectedReference(values),
                actual -> !exactContent(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(Iterable<? extends ELEMENT> expected) {
        var values = snapshot(expected, "expected elements");
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order", expectedReference(values),
                actual -> exactContentInAnyOrder(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(Iterable<? extends ELEMENT> unexpected) {
        var values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain exactly the unexpected elements in any order",
                "collection contained exactly the unexpected elements in any order", unexpectedReference(values),
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
        List<? extends ELEMENT> values = snapshot(expected, "expected elements");
        return preserving("collection contains expected prefix", "collection did not contain expected prefix", expectedReference(values),
                actual -> values.isEmpty() || actual.size() >= values.size() && prefixMatches(actual.iterator(), values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> endsWith(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = snapshot(expected, "expected elements");
        return preserving("collection contains expected suffix", "collection did not contain expected suffix", expectedReference(values),
                actual -> values.isEmpty() || actual.size() >= values.size() && prefixMatches(actual.reversed().iterator(), values.reversed()));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = snapshot(expected, "expected elements");
        return preserving("collection contains expected sequence", "collection did not contain expected sequence", expectedReference(values),
                actual -> containsSequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSequence(Iterable<? extends ELEMENT> unexpected) {
        List<? extends ELEMENT> values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain unexpected sequence", "collection contained unexpected sequence", unexpectedReference(values),
                actual -> !containsSequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSubsequence(Iterable<? extends ELEMENT> expected) {
        List<? extends ELEMENT> values = snapshot(expected, "expected elements");
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence", expectedReference(values),
                actual -> containsSubsequence(actual, values));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSubsequence(Iterable<? extends ELEMENT> unexpected) {
        List<? extends ELEMENT> values = snapshot(unexpected, "unexpected elements");
        return preserving("collection does not contain unexpected subsequence",
                "collection contained unexpected subsequence", unexpectedReference(values),
                actual -> !containsSubsequence(actual, values));
    }

    public static final SelectedCondition<SequencedCollection<?>> firstElement = selected("collection has a first element",
            actual -> selectElement(actual, 0));

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> firstElement(
            CheckedPredicate<? super ELEMENT> predicate) {
        return firstElement(Conditions.matches(predicate));
    }

    /** Checks the first element, retaining the source's element type. */
    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> firstElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("first element", nested, firstElement));
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> firstElement(
            Condition.ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("first element", nested, firstElement));
    }

    /**
     * Evaluates the first element once per attempt and returns the nested result.
     *
     * @param <ELEMENT> input type accepted by the nested condition
     * @param <RESULT> result produced by the nested condition
     * @param nested condition applied to the first element
     * @return an immutable condition with the nested result type
     */
    public static <ELEMENT, RESULT> Condition.Typed<SequencedCollection<? extends ELEMENT>, RESULT> firstElement(
            Condition.Typed<? super ELEMENT, RESULT> nested) {
        return ConditionSupport.compose("first element", nested, firstElement);
    }

    public static final SelectedCondition<SequencedCollection<?>> lastElement = selected("collection has a last element",
            CollectionConditions::selectLast);

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> lastElement(
            CheckedPredicate<? super ELEMENT> predicate) {
        return lastElement(Conditions.matches(predicate));
    }

    /** Checks the last element, retaining the source's element type. */
    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> lastElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("last element", nested, lastElement));
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> lastElement(
            Condition.ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("last element", nested, lastElement));
    }

    /**
     * Evaluates the last element once per attempt and returns the nested result.
     *
     * @param <ELEMENT> input type accepted by the nested condition
     * @param <RESULT> result produced by the nested condition
     * @param nested condition applied to the last element
     * @return an immutable condition with the nested result type
     */
    public static <ELEMENT, RESULT> Condition.Typed<SequencedCollection<? extends ELEMENT>, RESULT> lastElement(
            Condition.Typed<? super ELEMENT, RESULT> nested) {
        return ConditionSupport.compose("last element", nested, lastElement);
    }

    public static SelectedCondition<SequencedCollection<?>> elementAt(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        return selected("collection has an element at index " + index,
                actual -> selectElement(actual, index));
    }

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> elementAt(
            int index, CheckedPredicate<? super ELEMENT> predicate) {
        return elementAt(index, Conditions.matches(predicate));
    }

    /** Checks the indexed element, retaining the source's element type. */
    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> elementAt(
            int index, PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("element at index " + index, nested, elementAt(index)));
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> elementAt(
            int index, Condition.ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("element at index " + index, nested, elementAt(index)));
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
    public static <ELEMENT, RESULT> Condition.Typed<SequencedCollection<? extends ELEMENT>, RESULT> elementAt(int index,
            Condition.Typed<? super ELEMENT, RESULT> nested) {
        return ConditionSupport.compose("element at index " + index, nested, elementAt(index));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? extends ELEMENT>> sorted(Comparator<? super ELEMENT> comparator) {
        requireNonNull(comparator, "comparator must not be null");
        return ConditionSupport.preserving("collection is sorted", actual -> checkOrder(actual, comparator));
    }

    private static <ELEMENT> AttemptResult<ELEMENT> selectLast(SequencedCollection<? extends ELEMENT> actual) {
        return selectElement(actual == null ? null : actual.reversed(), 0);
    }

    private static <ELEMENT> AttemptResult<ELEMENT> selectElement(SequencedCollection<? extends ELEMENT> actual, int index) {
        if (actual == null) return unsatisfied("collection was null");
        int position = 0;
        for (ELEMENT value : actual) {
            if (position++ == index) {
                return satisfied(value);
            }
        }
        return unsatisfied("collection size was " + position);
    }

    private static PreservingCondition<Collection<?>> sized(int bound, IntPredicate matches,
            String relation) {
        return ConditionSupport.sized("collection", "size", bound, matches, relation, Collection::size);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            CheckedPredicate<? super VALUES> matches) {
        return preserving(description, mismatch, null, matches);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
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
        if (expected.isEmpty()) return true;
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

    private static boolean containsSubsequence(Collection<?> actual, List<?> expected) {
        if (expected.isEmpty()) return true;
        if (actual.size() < expected.size()) return false;
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

    private static <ELEMENT> ConditionResult checkOrder(SequencedCollection<ELEMENT> actual,
            Comparator<? super ELEMENT> comparator) {
        if (actual == null) return ConditionResult.unsatisfied("collection was null");
        Iterator<ELEMENT> values = actual.iterator();
        if (!values.hasNext()) {
            return ConditionResult.satisfied();
        }
        ELEMENT previous = values.next();
        long index = 1;
        while (values.hasNext()) {
            ELEMENT next = values.next();
            if (comparator.compare(previous, next) > 0) {
                return ConditionResult.unsatisfied("collection was not sorted at indices " + (index - 1) + " and " + index);
            }
            previous = next;
            index++;
        }
        return ConditionResult.satisfied();
    }

}
