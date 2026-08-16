package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedPredicate;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.containsAll;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.exactly;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.failure;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class CollectionCondition {

    public static final SingleElement singleElement = new SingleElement();
    public static final PreservingCondition<Collection<?>> noElements = sized(0, size -> size == 0,
            "collection is empty", "collection was non-empty");
    public static final PreservingCondition<Collection<?>> hasElements = sized(0, size -> size > 0,
            "collection is not empty", "collection was empty");
    public static final PreservingCondition<Collection<?>> containsNull = preserving(
            "collection contains null", "collection did not contain null", actual -> actual.contains(null));
    public static final PreservingCondition<Collection<?>> doesNotContainNull = preserving(
            "collection does not contain null", "collection contained null", actual -> !actual.contains(null));
    public static final PreservingCondition<Collection<?>> containsOnlyNulls = preserving(
            "collection contains only nulls", "collection did not contain only nulls",
            actual -> !actual.isEmpty() && matchesAll(actual, value -> value == null));
    public static final PreservingCondition<Collection<?>> hasNoDuplicates = preserving(
            "collection has no duplicates", "collection contained duplicates", CollectionCondition::hasUniqueElements);

    private CollectionCondition() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Collection<?>> elementCount(int expected) {
        return sized(expected, actual -> actual == expected,
                "collection size is " + expected, "collection size was not " + expected);
    }

    public static PreservingCondition<Collection<?>> elementCountNot(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected,
                "collection size is not " + unexpected, "collection size was " + unexpected);
    }

    public static PreservingCondition<Collection<?>> elementCountGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound,
                "collection size is greater than " + lowerBound,
                "collection size was not greater than " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> elementCountAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound,
                "collection size is at least " + lowerBound,
                "collection size was less than " + lowerBound);
    }

    public static PreservingCondition<Collection<?>> elementCountLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound,
                "collection size is less than " + upperBound,
                "collection size was not less than " + upperBound);
    }

    public static PreservingCondition<Collection<?>> elementCountAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound,
                "collection size is at most " + upperBound,
                "collection size was greater than " + upperBound);
    }

    public static PreservingCondition<Collection<?>> elementCountBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound);
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "collection size is between " + lowerBound + " and " + upperBound,
                "collection size was outside " + lowerBound + ".." + upperBound);
    }

    public static PreservingCondition<Collection<?>> sameElementCountAs(Collection<?> expected) {
        int expectedSize = requireNonNull(expected, "expected collection must not be null").size();
        return elementCount(expectedSize);
    }

    public static <E> Condition<Collection<E>, E> singleElement(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a single matching element", actual -> selectSingle(actual, predicate));
    }

    public static <R> Condition<Collection<?>, R> singleElement(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("collection has a single element of type " + type.getTypeName(), actual -> {
            var selected = selectSingle(actual, type::isInstance);
            return selected.status() == io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED
                    ? satisfied(type.cast(selected.result()))
                    : failure(selected);
        });
    }

    public static <E> PreservingCondition<Collection<E>> allElements(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate));
    }

    public static <E> PreservingCondition<Collection<E>> anyElement(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate));
    }

    public static <E> PreservingCondition<Collection<E>> noElement(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate));
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> contains(E... expected) {
        return membership(asList(nonEmpty(expected, "expected elements")), Match.ALL, true);
    }

    public static <E> PreservingCondition<Collection<? super E>> contains(Collection<? extends E> expected) {
        return membership(nonEmpty(expected, "expected elements"), Match.ALL, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(E... unexpected) {
        return membership(asList(nonEmpty(unexpected, "unexpected elements")), Match.ANY, false);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(Collection<? extends E> unexpected) {
        return membership(nonEmpty(unexpected, "unexpected elements"), Match.ANY, false);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(E... unexpected) {
        return membership(asList(nonEmpty(unexpected, "unexpected elements")), Match.ALL, false);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(
            Collection<? extends E> unexpected) {
        return membership(nonEmpty(unexpected, "unexpected elements"), Match.ALL, false);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsAny(E... expected) {
        return membership(asList(nonEmpty(expected, "expected elements")), Match.ANY, true);
    }

    public static <E> PreservingCondition<Collection<? super E>> containsAny(Collection<? extends E> expected) {
        return membership(nonEmpty(expected, "expected elements"), Match.ANY, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactly(E... expected) {
        return exact(asList(nonNull(expected, "expected elements")), true, true);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactly(Collection<? extends E> expected) {
        return exact(nonNull(expected, "expected elements"), true, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactly(E... expected) {
        return exact(asList(nonNull(expected, "expected elements")), true, false);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactly(Collection<? extends E> expected) {
        return exact(nonNull(expected, "expected elements"), true, false);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrder(E... expected) {
        return exact(asList(nonNull(expected, "expected elements")), false, true);
    }

    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrder(Collection<? extends E> expected) {
        return exact(nonNull(expected, "expected elements"), false, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrder(E... expected) {
        return exact(asList(nonNull(expected, "expected elements")), false, false);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrder(Collection<? extends E> expected) {
        return exact(nonNull(expected, "expected elements"), false, false);
    }

    @SafeVarargs
    public static <E> PreservingCondition<Collection<? super E>> containsOnly(E... expected) {
        return containsOnly(asList(nonNull(expected, "expected elements")));
    }

    public static <E> PreservingCondition<Collection<? super E>> containsOnly(Collection<? extends E> expected) {
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
    public static <E> PreservingCondition<SequencedCollection<? super E>> startsWithElements(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.PREFIX, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> endsWithElements(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.SUFFIX, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsSequence(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.SEQUENCE, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainSequence(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.SEQUENCE, false);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsSubsequence(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.SUBSEQUENCE, true);
    }

    @SafeVarargs
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainSubsequence(E... expected) {
        return ordered(asList(nonEmpty(expected, "expected elements")), Order.SUBSEQUENCE, false);
    }

    public static <E> Condition<SequencedCollection<E>, E> first() {
        return position("first", SequencedCollection::getFirst);
    }

    public static <E> Condition<SequencedCollection<E>, E> first(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a matching element", actual -> selectFirst(actual, predicate));
    }

    public static <E> Condition<SequencedCollection<E>, E> last() {
        return position("last", SequencedCollection::getLast);
    }

    public static <E> Condition<SequencedCollection<E>, E> last(CheckedPredicate<? super E> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a last matching element",
                actual -> selectFirst(actual == null ? null : actual.reversed(), predicate));
    }

    public static <E> Condition<List<E>, E> element(int index) {
        return element(index, value -> true);
    }

    public static <E> Condition<List<E>, E> element(int index, CheckedPredicate<? super E> predicate) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
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

    private static <E> io.github.gromoff97.awium.conditioning.Evaluation<E> selectSingle(Collection<E> actual,
            CheckedPredicate<? super E> predicate) throws Exception {
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

    private static <E> io.github.gromoff97.awium.conditioning.Evaluation<E> selectFirst(Iterable<E> actual,
            CheckedPredicate<? super E> predicate) throws Exception {
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

    private static <E> Condition<SequencedCollection<E>, E> position(String name,
            java.util.function.Function<SequencedCollection<E>, E> selector) {
        return condition("collection has a " + name + " element", actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return actual.isEmpty() ? unsatisfied("collection was empty") : satisfied(selector.apply(actual));
        });
    }

    private static PreservingCondition<Collection<?>> sized(int bound, java.util.function.IntPredicate matches,
            String description, String mismatch) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        return new PreservingCondition<>(condition(description, actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            int size = actual.size();
            return matches.test(size) ? satisfied(actual) : unsatisfied("collection size was " + size);
        }));
    }

    private static <C extends Collection<?>> PreservingCondition<C> preserving(String description, String mismatch,
            CheckedPredicate<? super C> matches) {
        return new PreservingCondition<>(condition(description, actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch);
        }));
    }

    private static <E> PreservingCondition<Collection<? super E>> membership(Collection<? extends E> expected,
            Match match, boolean positive) {
        String target = expected.size() == 1 ? "expected element"
                : match == Match.ALL ? "all expected elements" : "an expected element";
        String description = "collection " + (positive ? "contains " : "does not contain ") + target;
        String mismatch = "collection " + (positive ? "did not contain " : "contained ") + target;
        return preserving(description, mismatch, actual -> {
            boolean matched = match == Match.ALL
                    ? containsAll(actual, new ArrayList<>(expected), ValueMatching::equal)
                    : matchesAny(actual, value -> matchesAny(expected, candidate -> equal(value, candidate)));
            return matched == positive;
        });
    }

    private static <C extends Collection<?>> PreservingCondition<C> exact(Collection<?> expected, boolean ordered,
            boolean positive) {
        String order = ordered ? "" : " in any order";
        return preserving("collection " + (positive ? "contains " : "does not contain ")
                        + "exactly the expected elements" + order,
                "collection " + (positive ? "did not contain" : "contained")
                        + " exactly the expected elements" + order,
                actual -> exactContent(actual, expected, ordered) == positive);
    }

    private static <E> PreservingCondition<SequencedCollection<? super E>> ordered(List<? extends E> expected,
            Order order, boolean positive) {
        String target = order.name().toLowerCase(java.util.Locale.ROOT);
        return preserving("collection " + (positive ? "contains " : "does not contain ") + "expected " + target,
                "collection " + (positive ? "did not contain " : "contained ") + "expected " + target,
                actual -> ordered(actual, expected, order) == positive);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected, boolean ordered) throws Exception {
        if (actual.size() != expected.size()) {
            return false;
        }
        if (ordered) {
            Iterator<?> left = actual.iterator();
            Iterator<?> right = expected.iterator();
            while (left.hasNext()) {
                if (!equal(left.next(), right.next())) {
                    return false;
                }
            }
            return true;
        }
        return exactly(actual.iterator(), new ArrayList<>(expected), ValueMatching::equal);
    }

    private static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Exception {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> equal(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> equal(value, candidate)));
    }

    private static boolean ordered(SequencedCollection<?> actual, List<?> expected, Order order) {
        List<?> values = actual instanceof List<?> list ? list : new ArrayList<>(actual);
        return switch (order) {
            case PREFIX -> regionMatches(values, 0, expected);
            case SUFFIX -> regionMatches(values, values.size() - expected.size(), expected);
            case SEQUENCE -> containsSequence(values, expected);
            case SUBSEQUENCE -> containsSubsequence(values, expected);
        };
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

    private static void validateRange(int lowerBound, int upperBound) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException("size range must be non-negative and ordered");
        }
    }

    private static <T> T nonNull(T value, String name) {
        return requireNonNull(value, name + " must not be null");
    }

    private static <E> E[] nonEmpty(E[] values, String name) {
        nonNull(values, name);
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    private static <E, C extends Collection<? extends E>> C nonEmpty(C values, String name) {
        nonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    public static final class SingleElement {

        private SingleElement() {
        }

        public Evaluation<Collection<?>> evaluate(Collection<?> actual) {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return actual.size() == 1
                    ? satisfied(actual)
                    : unsatisfied("collection size was " + actual.size());
        }

        public String description() {
            return "collection has a single element";
        }

        public Explained because(String explanation) {
            return new Explained(this, explanation);
        }

        public Explained because(String format, Object... arguments) {
            return new Explained(this, formattedExplanation(format, arguments));
        }

        public record Explained(SingleElement delegate, String explanation) {

            public Explained {
                requireNonNull(delegate, "condition must not be null");
                explanation = literalExplanation(explanation);
            }
        }
    }

    private enum Match { ALL, ANY }

    private enum Order { PREFIX, SUFFIX, SEQUENCE, SUBSEQUENCE }
}
