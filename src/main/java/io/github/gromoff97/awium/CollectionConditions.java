package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionResult.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.CheckedCalls.call;
import static io.github.gromoff97.awium.CheckedCalls.checkInterruption;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.ValueMatching.equal;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.ValueRendering.snapshot;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class CollectionConditions {

    public static final SelectedCondition<Collection<?>, Collection<?>> single = selected("collection has a single element", actual -> {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        int size = call(actual::size);
        if (size != 1) {
            return unsatisfied("collection size was " + size);
        }
        var values = call(actual::iterator);
        return satisfied(call(values::next));
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
            actual -> !call(actual::isEmpty) && matchesAll(actual, value -> value == null));
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

    public static PreservingCondition<Collection<?>> sameSizeAs(Collection<?> expected) {
        Collection<?> values = requireNonNull(expected, "expected collection must not be null");
        return preserving("collection has the same size as expected collection",
                "collection size did not match expected collection", expectedReference(values),
                actual -> {
                    int size = call(actual::size);
                    return size == call(values::size);
                });
    }

    public static <Element> Condition<Collection<Element>, Element> single(PreservingCondition<? super Element> nested) {
        return ConditionSupport.single("collection", nested, actual -> actual,
                () -> nested.preservingSession());
    }

    public static <Element, Expected extends Element> Condition<Collection<Element>, Element> single(ExpectedCondition<Expected> nested) {
        return ConditionSupport.single("collection", nested, actual -> actual,
                () -> nested.preservingSession());
    }

    public static <Element, Result extends Element> Condition<Collection<Element>, Result> single(NarrowingCondition<Result> nested) {
        return ConditionSupport.single("collection", nested, actual -> actual,
                () -> nested.newSession());
    }

    public static <Element, Result> Condition<Collection<Element>, Result> single(Condition<? super Element, ? extends Result> nested) {
        return ConditionSupport.single("collection", nested, actual -> actual,
                () -> nested.newSession());
    }

    public static <Element> PreservingCondition<Collection<Element>> all(Predicate<? super Element> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all collection elements match", "not all collection elements matched",
                actual -> matchesAll(actual, predicate::test));
    }

    public static <Element> PreservingCondition<Collection<Element>> any(Predicate<? super Element> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any collection element matches", "no collection element matched",
                actual -> matchesAny(actual, predicate::test));
    }

    public static <Element> PreservingCondition<Collection<Element>> none(Predicate<? super Element> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no collection element matches", "a collection element matched",
                actual -> !matchesAny(actual, predicate::test));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> contains(Element... expected) {
        return contains(asList(nonEmpty(expected, "expected elements")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> contains(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(nonEmpty(expected, "expected elements").stream());
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection contains " + target, "collection did not contain " + target, expectedReference(values),
                actual -> ValueMatching.containsAll(actual, values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContain(Element... unexpected) {
        return doesNotContain(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContain(Collection<? extends Element> unexpected) {
        Collection<? extends Element> values = snapshot(nonEmpty(unexpected, "unexpected elements").stream());
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection does not contain " + target, "collection contained " + target, unexpectedReference(values),
                actual -> !matchesAny(actual,
                        value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContainAll(Element... unexpected) {
        return doesNotContainAll(asList(nonEmpty(unexpected, "unexpected elements")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContainAll(Collection<? extends Element> unexpected) {
        Collection<? extends Element> values = snapshot(nonEmpty(unexpected, "unexpected elements").stream());
        String target = values.size() == 1 ? "expected element" : "all expected elements";
        return preserving("collection does not contain " + target, "collection contained " + target, unexpectedReference(values),
                actual -> !ValueMatching.containsAll(actual, values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> containsAny(Element... expected) {
        return containsAny(asList(nonEmpty(expected, "expected elements")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> containsAny(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(nonEmpty(expected, "expected elements").stream());
        String target = values.size() == 1 ? "expected element" : "an expected element";
        return preserving("collection contains " + target, "collection did not contain " + target, expectedReference(values),
                actual -> matchesAny(actual,
                        value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> containsExactly(Element... expected) {
        return containsExactly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <Element> PreservingCondition<SequencedCollection<? super Element>> containsExactly(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection contains exactly the expected elements",
                "collection did not contain exactly the expected elements", expectedReference(values),
                actual -> exactContent(actual, values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> doesNotContainExactly(Element... expected) {
        return doesNotContainExactly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <Element> PreservingCondition<SequencedCollection<? super Element>> doesNotContainExactly(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection does not contain exactly the expected elements",
                "collection contained exactly the expected elements", expectedReference(values),
                actual -> !exactContent(actual, values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> containsExactlyInAnyOrder(Element... expected) {
        return containsExactlyInAnyOrder(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> containsExactlyInAnyOrder(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection contains exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order", expectedReference(values),
                actual -> exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContainExactlyInAnyOrder(Element... expected) {
        return doesNotContainExactlyInAnyOrder(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> doesNotContainExactlyInAnyOrder(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection does not contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order", expectedReference(values),
                actual -> !exactContentInAnyOrder(actual, values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<Collection<? super Element>> containsOnly(Element... expected) {
        return containsOnly(asList(requireNonNull(expected, "expected elements must not be null")));
    }

    public static <Element> PreservingCondition<Collection<? super Element>> containsOnly(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection contains only the expected elements",
                "collection did not contain only the expected elements", expectedReference(values),
                actual -> sameDistinctElements(actual, values));
    }

    public static <Element> PreservingCondition<Collection<? extends Element>> subsetOf(Collection<? extends Element> expected) {
        Collection<? extends Element> values = snapshot(requireNonNull(expected, "expected elements must not be null").stream());
        return preserving("collection is a subset of the expected elements",
                "collection was not a subset of the expected elements", expectedReference(values),
                actual -> matchesAll(actual, value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> startsWith(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection contains expected prefix", "collection did not contain expected prefix", expectedReference(values),
                actual -> regionMatches(elements(actual), 0, values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> endsWith(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection contains expected suffix", "collection did not contain expected suffix", expectedReference(values),
                actual -> {
                    List<?> actualElements = elements(actual);
                    return regionMatches(actualElements, call(actualElements::size) - values.size(), values);
                });
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> containsSequence(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection contains expected sequence", "collection did not contain expected sequence", expectedReference(values),
                actual -> containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> doesNotContainSequence(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection does not contain expected sequence", "collection contained expected sequence", expectedReference(values),
                actual -> !containsSequence(elements(actual), values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> containsSubsequence(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection contains expected subsequence", "collection did not contain expected subsequence", expectedReference(values),
                actual -> containsSubsequence(elements(actual), values));
    }

    @SafeVarargs
    public static <Element> PreservingCondition<SequencedCollection<? super Element>> doesNotContainSubsequence(Element... expected) {
        List<? extends Element> values = snapshot(stream(nonEmpty(expected, "expected elements")));
        return preserving("collection does not contain expected subsequence",
                "collection contained expected subsequence", expectedReference(values),
                actual -> !containsSubsequence(elements(actual), values));
    }

    public static final SelectedCondition<SequencedCollection<?>, Collection<?>> first = position("first", SequencedCollection::getFirst);

    public static <Element> Condition<SequencedCollection<Element>, Element> first(Predicate<? super Element> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a matching element", actual -> selectFirst(actual, predicate));
    }

    public static final SelectedCondition<SequencedCollection<?>, Collection<?>> last = position("last", SequencedCollection::getLast);

    public static <Element> Condition<SequencedCollection<Element>, Element> last(Predicate<? super Element> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("collection has a last matching element",
                actual -> selectFirst(actual == null ? null : call(actual::reversed), predicate));
    }

    public static <Element> Condition<List<Element>, Element> element(int index) {
        return element(index, value -> true);
    }

    public static <Element> Condition<List<Element>, Element> element(int index, Predicate<? super Element> predicate) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        requireNonNull(predicate, "predicate must not be null");
        return condition("list has a matching element at index " + index, actual -> {
            if (actual == null) {
                return unsatisfied("list was null");
            }
            int size = call(actual::size);
            if (size <= index) {
                return unsatisfied("list size was " + size);
            }
            Element value = call(() -> actual.get(index));
            return call(() -> predicate.test(value)) ? satisfied(value) : unsatisfied("element at index " + index + " did not match");
        });
    }

    public static <Element extends Comparable<? super Element>> PreservingCondition<SequencedCollection<Element>> sorted() {
        return sorted(Comparator.naturalOrder());
    }

    public static <Element> PreservingCondition<SequencedCollection<Element>> sorted(Comparator<? super Element> comparator) {
        requireNonNull(comparator, "comparator must not be null");
        return preserving("collection is sorted", "collection was not sorted", actual -> isSorted(actual, comparator));
    }

    private static <Element> ConditionResult<Element> selectFirst(Iterable<Element> actual,
            Predicate<? super Element> predicate) throws Exception {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        for (var iterator = call(actual::iterator); call(iterator::hasNext);) {
            Element value = call(iterator::next);
            boolean matching = call(() -> predicate.test(value));
            if (matching) {
                return satisfied(value);
            }
        }
        return unsatisfied("no collection element matched");
    }

    private static SelectedCondition<SequencedCollection<?>, Collection<?>> position(String name,
            Function<SequencedCollection<?>, ?> selector) {
        return selected("collection has a " + name + " element", actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            return call(actual::isEmpty)
                    ? unsatisfied("collection was empty")
                    : satisfied(call(() -> selector.apply(actual)));
        });
    }

    private static PreservingCondition<Collection<?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("collection", bound, matches, description, Collection::size);
    }

    private static <Values extends Collection<?>> PreservingCondition<Values> preserving(String description, String mismatch,
            CheckedFunction<? super Values, Boolean> matches) {
        return preservingNonNull("collection", description, mismatch, matches);
    }

    private static <Values extends Collection<?>> PreservingCondition<Values> preserving(String description, String mismatch,
            Reference<?> reference, CheckedFunction<? super Values, Boolean> matches) {
        return preservingNonNull("collection", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) throws Exception {
        if (call(actual::size) != expected.size()) {
            return false;
        }
        Iterator<?> left = call(actual::iterator);
        Iterator<?> right = expected.iterator();
        while (call(left::hasNext) && right.hasNext()) {
            if (!equal(call(left::next), right.next())) {
                return false;
            }
        }
        return !call(left::hasNext) && !right.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) throws Exception {
        return call(actual::size) == expected.size()
                && exactly(call(actual::iterator), expected, ValueMatching::equal);
    }

    private static List<?> elements(SequencedCollection<?> actual) throws Exception {
        return actual instanceof List<?> list ? list : call(() -> new ArrayList<>(actual));
    }

    private static boolean containsSequence(List<?> actual, List<?> expected) throws Exception {
        for (int start = 0; start <= call(actual::size) - expected.size(); start++) {
            if (regionMatches(actual, start, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSubsequence(List<?> actual, List<?> expected) throws Exception {
        int expectedIndex = 0;
        for (var iterator = call(actual::iterator); call(iterator::hasNext);) {
            Object value = call(iterator::next);
            if (equal(value, expected.get(expectedIndex)) && ++expectedIndex == expected.size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean regionMatches(List<?> actual, int start, List<?> expected) throws Exception {
        if (start < 0 || start + expected.size() > call(actual::size)) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            int actualIndex = start + index;
            if (!equal(call(() -> actual.get(actualIndex)), expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUniqueElements(Collection<?> actual) throws Exception {
        List<Object> seen = new ArrayList<>();
        for (var iterator = call(actual::iterator); call(iterator::hasNext);) {
            Object value = call(iterator::next);
            if (matchesAny(seen, previous -> equal(previous, value))) {
                return false;
            }
            seen.add(value);
        }
        return true;
    }

    private static <Element> boolean isSorted(SequencedCollection<Element> actual, Comparator<? super Element> comparator) throws Exception {
        Iterator<Element> values = call(actual::iterator);
        if (!call(values::hasNext)) {
            return true;
        }
        Element previous = call(values::next);
        while (call(values::hasNext)) {
            Element next = call(values::next);
            checkInterruption();
            int comparison = comparator.compare(previous, next);
            if (comparison > 0) {
                return false;
            }
            previous = next;
        }
        return true;
    }

}
