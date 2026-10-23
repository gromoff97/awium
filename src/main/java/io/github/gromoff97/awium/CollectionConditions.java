package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static java.util.Objects.deepEquals;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class CollectionConditions {

    public static final SelectedCondition<Collection<?>> single = selected("collection has a single element", actual -> {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        int size = actual.size();
        if (size != 1) {
            return unsatisfied("collection size was " + size);
        }
        var values = actual.iterator();
        return satisfied(values.next());
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
        return selected(ConditionSupport.single("collection", required(nested).definition, actual -> actual,
                () -> nested.preservingAttempt()));
    }

    public static <ELEMENT, RESULT> Condition<Collection<? extends ELEMENT>, RESULT> single(Condition<? super ELEMENT, ? extends RESULT> nested) {
        return ConditionSupport.single("collection", required(nested), actual -> actual,
                () -> nested.newAttempt());
    }

    public static <EXPECTED> SelectedCondition<Collection<? super EXPECTED>> single(ComparisonCondition<EXPECTED> nested) {
        return selected(ConditionSupport.single("collection", required(nested).definition, actual -> actual,
                () -> nested.definition.preservingAttempt()));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate::test));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate::test));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate::test));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(ELEMENT... expected) {
        return contains(asList(nonEmpty(expected, "expected elements")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = nonEmpty(expected, "expected elements").stream().toList();
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection contains " + target, "collection did not contain " + target, expectedReference(values),
                actual -> ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(ELEMENT... unexpected) {
        return doesNotContain(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(Collection<? extends ELEMENT> unexpected) {
        Collection<? extends ELEMENT> values = nonEmpty(unexpected, "unexpected elements").stream().toList();
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection does not contain " + target, "collection contained " + target, unexpectedReference(values),
                actual -> !matchesAny(actual,
                        value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(ELEMENT... unexpected) {
        return doesNotContainAll(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(Collection<? extends ELEMENT> unexpected) {
        Collection<? extends ELEMENT> values = nonEmpty(unexpected, "unexpected elements").stream().toList();
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection does not contain " + target, "collection contained " + target, unexpectedReference(values),
                actual -> !ValueMatching.containsAll(actual, values, Objects::deepEquals));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(ELEMENT... expected) {
        return containsAny(asList(nonEmpty(expected, "expected elements")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = nonEmpty(expected, "expected elements").stream().toList();
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection contains " + target, "collection did not contain " + target, expectedReference(values),
                actual -> matchesAny(actual,
                        value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(ELEMENT... expected) {
        return containsExactly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements", expectedReference(values),
                actual -> exactContent(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(ELEMENT... expected) {
        return doesNotContainExactly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection does not contain exactly the expected elements",
                "collection contained exactly the expected elements", expectedReference(values),
                actual -> !exactContent(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(ELEMENT... expected) {
        return containsExactlyInAnyOrder(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order", expectedReference(values),
                actual -> exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(ELEMENT... expected) {
        return doesNotContainExactlyInAnyOrder(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection does not contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order", expectedReference(values),
                actual -> !exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsOnly(ELEMENT... expected) {
        return containsOnly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsOnly(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection contains only the expected elements",
                "collection did not contain only the expected elements", expectedReference(values),
                actual -> sameDistinctElements(actual, values));
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> subsetOf(Collection<? extends ELEMENT> expected) {
        Collection<? extends ELEMENT> values = requireNonNull(expected, "expected elements must not be null").stream().toList();
        return preserving("collection is a subset of the expected elements",
                "collection was not a subset of the expected elements", expectedReference(values),
                actual -> matchesAll(actual, value -> matchesAny(values, candidate -> deepEquals(value, candidate))));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> startsWith(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection contains expected prefix", "collection did not contain expected prefix", expectedReference(values),
                actual -> regionMatches(elements(actual), 0, values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> endsWith(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection contains expected suffix", "collection did not contain expected suffix", expectedReference(values),
                actual -> {
                    List<?> actualElements = elements(actual);
                    return regionMatches(actualElements, actualElements.size() - values.size(), values);
                });
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection contains expected sequence", "collection did not contain expected sequence", expectedReference(values),
                actual -> containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection does not contain expected sequence", "collection contained expected sequence", expectedReference(values),
                actual -> !containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSubsequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence", expectedReference(values),
                actual -> containsSubsequence(elements(actual), values));
    }

    @SafeVarargs
    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSubsequence(ELEMENT... expected) {
        List<? extends ELEMENT> values = stream(nonEmpty(expected, "expected elements")).toList();
        return preserving("collection does not contain expected subsequence",
                "collection contained expected subsequence", expectedReference(values),
                actual -> !containsSubsequence(elements(actual), values));
    }

    public static final SelectedCondition<SequencedCollection<?>> first = position("first", SequencedCollection::getFirst);

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> first(Predicate<? super ELEMENT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("collection has a matching element", actual -> selectFirst(actual, predicate));
    }

    public static final SelectedCondition<SequencedCollection<?>> last = position("last", SequencedCollection::getLast);

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
            int size = actual.size();
            if (size <= index) {
                return unsatisfied("list size was " + size);
            }
            ELEMENT value = actual.get(index);
            return predicate.test(value) ? satisfied(value) : unsatisfied("element at index " + index + " did not match");
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

    private static SelectedCondition<SequencedCollection<?>> position(String name,
            Function<SequencedCollection<?>, ?> selector) {
        return selected("collection has a " + name + " element", actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return actual.isEmpty()
                    ? unsatisfied("collection was empty")
                    : satisfied(selector.apply(actual));
        });
    }

    private static PreservingCondition<Collection<?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("collection", "size", bound, matches, description, Collection::size);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            CheckedFunction<? super VALUES, Boolean> matches) {
        return preservingNonNull("collection", description, mismatch, matches);
    }

    private static <VALUES extends Collection<?>> PreservingCondition<VALUES> preserving(String description, String mismatch,
            Reference<?> reference, CheckedFunction<? super VALUES, Boolean> matches) {
        return preservingNonNull("collection", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> left = actual.iterator();
        Iterator<?> right = expected.iterator();
        while (left.hasNext() && right.hasNext()) {
            if (!deepEquals(left.next(), right.next())) {
                return false;
            }
        }
        return !left.hasNext() && !right.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) {
        return actual.size() == expected.size()
                && exactly(actual.iterator(), expected, Objects::deepEquals);
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
            if (deepEquals(value, expected.get(expectedIndex)) && ++expectedIndex == expected.size()) {
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
            if (!deepEquals(actual.get(start + index), expected.get(index))) {
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
