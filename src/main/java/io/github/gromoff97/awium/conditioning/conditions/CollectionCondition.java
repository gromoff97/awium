package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.exactly;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.nonNull;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.selected;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class CollectionCondition {

    public static final SelectedCondition<Collection<?>, CollectionSource<?>> single = selected("collection has a single element", actual -> {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        return actual.size() == 1
                ? satisfied(actual.iterator().next())
                : unsatisfied("collection size was " + actual.size());
    });
    public static final PreservingCondition<Collection<?>> empty = sized(0, size -> size == 0,
            "collection is empty");
    public static final PreservingCondition<Collection<?>> nonEmpty = sized(0, size -> size > 0,
            "collection is not empty");
    public static final PreservingCondition<Collection<?>> containsNull = preserving("collection contains null",
            "collection did not contain null", actual -> matchesAny(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> doesNotContainNull = preserving("collection does not contain null",
            "collection contained null", actual -> !matchesAny(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> containsOnlyNulls = preserving("collection contains only nulls",
            "collection did not contain only nulls",
            actual -> !actual.isEmpty() && matchesAll(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> hasNoDuplicates = preserving("collection has no duplicates",
            "collection contained duplicates", CollectionCondition::hasUniqueElements);

    private CollectionCondition() {
        throw new AssertionError("Utility class");
    }

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

    public static PreservingCondition<Collection<?>> sameSizeAs(Collection<?> expected) {
        int expectedSize = requireNonNull(expected, "expected collection must not be null").size();
        return size(expectedSize);
    }

    public static <E> Condition<Collection<E>, E> single(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a single matching element", actual -> selectSingle(actual, predicate));
    }

    public static <R> Condition<Collection<?>, R> singleElementOfType(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("collection has a single element of type " + type.getTypeName(), actual ->
                selectSingle(actual, type::isInstance)
                        .continueIfSatisfied(value -> satisfied(type.cast(value))));
    }

    public static <E> PreservingCondition<Collection<E>> all(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate));
    }

    public static <E> PreservingCondition<Collection<E>> any(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate));
    }

    public static <E> PreservingCondition<Collection<E>> none(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> contains(E... expected) {
        return containsAll(asList(nonEmpty(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> containsAll(Collection<? extends E> expected) {
        Collection<? extends E> values = nonEmpty(expected, "expected elements");
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection contains " + target, "collection did not contain " + target,
                actual -> ValueMatching.containsAll(actual, values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(E... unexpected) {
        return doesNotContainAnyElementsOf(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAnyElementsOf(Collection<? extends E> unexpected) {
        Collection<? extends E> values = nonEmpty(unexpected, "unexpected elements");
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection does not contain " + target, "collection contained " + target,
                actual -> !matchesAny(actual,
                        value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(E... unexpected) {
        return doesNotContainAllElementsOf(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAllElementsOf(Collection<? extends E> unexpected) {
        Collection<? extends E> values = nonEmpty(unexpected, "unexpected elements");
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection does not contain " + target, "collection contained " + target,
                actual -> !ValueMatching.containsAll(actual, values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsAnyOf(E... expected) {
        return containsAnyElementsOf(asList(nonEmpty(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> containsAnyElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonEmpty(expected, "expected elements");
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection contains " + target, "collection did not contain " + target,
                actual -> matchesAny(actual,
                        value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactly(E... expected) {
        return containsExactlyElementsOf(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactlyElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements",
                actual -> exactContent(actual, values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactly(E... expected) {
        return doesNotContainExactlyElementsOf(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactlyElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements",
                "collection contained exactly the expected elements",
                actual -> !exactContent(actual, values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrder(E... expected) {
        return containsExactlyInAnyOrderElementsOf(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrderElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order",
                actual -> exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrder(E... expected) {
        return doesNotContainExactlyInAnyOrderElementsOf(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrderElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection does not contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order",
                actual -> !exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsOnly(E... expected) {
        return containsOnlyElementsOf(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> containsOnlyElementsOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection contains only the expected elements",
                "collection did not contain only the expected elements",
                actual -> sameDistinctElements(actual, values));
    }

    public static <E> PreservingCondition<Collection<? extends E>> subsetOf(Collection<? extends E> expected) {
        Collection<? extends E> values = nonNull(expected, "expected elements");
        return preserving("collection is a subset of the expected elements",
                "collection was not a subset of the expected elements",
                actual -> matchesAll(actual, value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> startsWith(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection contains expected prefix", "collection did not contain expected prefix",
                actual -> regionMatches(elements(actual), 0, values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> endsWith(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection contains expected suffix", "collection did not contain expected suffix",
                actual -> {
                    List<?> actualElements = elements(actual);
                    return regionMatches(actualElements, actualElements.size() - values.size(), values);
                });
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsSequence(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection contains expected sequence", "collection did not contain expected sequence",
                actual -> containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainSequence(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection does not contain expected sequence", "collection contained expected sequence",
                actual -> !containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsSubsequence(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence",
                actual -> containsSubsequence(elements(actual), values));
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainSubsequence(E... expected) {
        List<? extends E> values = asList(nonEmpty(expected, "expected elements"));
        return preserving("collection does not contain expected subsequence",
                "collection contained expected subsequence",
                actual -> !containsSubsequence(elements(actual), values));
    }

    public static final SelectedCondition<SequencedCollection<?>, CollectionSource<?>> first = position("first", SequencedCollection::getFirst);

    public static <E> Condition<SequencedCollection<E>, E> first(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a matching element", actual -> selectFirst(actual, predicate));
    }

    public static final SelectedCondition<SequencedCollection<?>, CollectionSource<?>> last = position("last", SequencedCollection::getLast);

    public static <E> Condition<SequencedCollection<E>, E> last(Predicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a last matching element",
                actual -> selectFirst(actual == null ? null : actual.reversed(), predicate));
    }

    public static <E> Condition<List<E>, E> element(int index) {
        return element(index, value -> true);
    }

    public static <E> Condition<List<E>, E> element(int index, Predicate<? super E> predicate) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        requireNonNull(predicate, "predicate must not be null");
        return condition("list has a matching element at index " + index, actual -> {
            if (actual == null) {
                return unsatisfied("list was null");
            }
            if (actual.size() <= index) {
                return unsatisfied("list size was " + actual.size());
            }
            E value = actual.get(index);
            return predicate.test(value) ? satisfied(value) : unsatisfied("element at index " + index + " did not match");
        });
    }

    public static <E extends Comparable<? super E>> PreservingCondition<SequencedCollection<E>> sorted() {
        return sorted(Comparator.naturalOrder());
    }

    public static <E> PreservingCondition<SequencedCollection<E>> sorted(Comparator<? super E> comparator) {
        requireNonNull(comparator, "comparator must not be null");
        return preserving("collection is sorted", "collection was not sorted", actual -> isSorted(actual, comparator));
    }

    private static <E> Evaluation<E> selectSingle(Collection<E> actual,
            Predicate<? super E> predicate) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        E selected = null;
        boolean found = false;
        for (E value : actual) {
            if (!predicate.test(value)) {
                continue;
            }
            if (found) {
                return unsatisfied("more than one collection element matched");
            }
            selected = value;
            found = true;
        }
        return found ? satisfied(selected) : unsatisfied("no collection element matched");
    }

    private static <E> Evaluation<E> selectFirst(Iterable<E> actual,
            Predicate<? super E> predicate) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        for (E value : actual) {
            if (predicate.test(value)) {
                return satisfied(value);
            }
        }
        return unsatisfied("no collection element matched");
    }

    private static SelectedCondition<SequencedCollection<?>, CollectionSource<?>> position(String name,
            java.util.function.Function<SequencedCollection<?>, ?> selector) {
        return selected("collection has a " + name + " element", actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return actual.isEmpty()
                    ? unsatisfied("collection was empty")
                    : satisfied(selector.apply(actual));
        });
    }

    private static PreservingCondition<Collection<?>> sized(int bound, java.util.function.IntPredicate matches,
            String description) {
        return ConditionSupport.sized("collection", bound, matches, description, Collection::size);
    }

    private static <C extends Collection<?>> PreservingCondition<C> preserving(String description, String mismatch,
            Predicate<? super C> matches) {
        return preservingNonNull("collection", description, mismatch, matches);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> left = actual.iterator();
        Iterator<?> right = expected.iterator();
        while (left.hasNext()) {
            if (!equal(left.next(), right.next())) {
                return false;
            }
        }
        return true;
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        return exactly(actual.iterator(), expected, ValueMatching::equal);
    }

    private static List<?> elements(SequencedCollection<?> actual) {
        return actual instanceof List<?> list ? list : new ArrayList<>(actual);
    }

    private static boolean containsSequence(List<?> actual, List<?> expected) {
        for (int start = 0; start <= actual.size() - expected.size(); start++) {
            if (regionMatches(actual, start, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSubsequence(List<?> actual, List<?> expected) {
        int expectedIndex = 0;
        for (Object value : actual) {
            if (equal(value, expected.get(expectedIndex)) && ++expectedIndex == expected.size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean regionMatches(List<?> actual, int start, List<?> expected) {
        if (start < 0 || start + expected.size() > actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!equal(actual.get(start + index), expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUniqueElements(Collection<?> actual) {
        List<Object> seen = new ArrayList<>();
        for (Object value : actual) {
            if (seen.stream().anyMatch(previous -> equal(previous, value))) {
                return false;
            }
            seen.add(value);
        }
        return true;
    }

    private static <E> boolean isSorted(SequencedCollection<E> actual, Comparator<? super E> comparator) {
        Iterator<E> values = actual.iterator();
        if (!values.hasNext()) {
            return true;
        }
        E previous = values.next();
        while (values.hasNext()) {
            E next = values.next();
            if (comparator.compare(previous, next) > 0) {
                return false;
            }
            previous = next;
        }
        return true;
    }

}
