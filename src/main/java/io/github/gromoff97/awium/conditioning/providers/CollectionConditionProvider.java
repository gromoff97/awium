package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public final class CollectionConditionProvider {

    private CollectionConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <E> PreservingCondition<Collection<? super E>> contains(
            E expected) {
        return membership(singletonList(expected), false, true,
                "collection to contain expected element",
                "collection did not contain expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(
            E expected) {
        return membership(singletonList(expected), false, false,
                "collection not to contain expected element",
                "collection contained expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAll(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), true, true,
                "collection to contain all expected elements",
                "collection did not contain all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), true, false,
                "collection not to contain all expected elements",
                "collection contained all expected elements");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAllElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), true, true,
                "collection to contain all expected elements",
                "collection did not contain all expected elements");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAllElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), true, false,
                "collection not to contain all expected elements",
                "collection contained all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAnyOf(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), false, true,
                "collection to contain any expected element",
                "collection did not contain any expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsNoneOf(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), false, false,
                "collection to contain none of the expected elements",
                "collection contained an expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAnyElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), false, true,
                "collection to contain any expected element",
                "collection did not contain any expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsNoElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), false, false,
                "collection to contain none of the expected elements",
                "collection contained an expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactly(E... expected) {
        return exact(Arrays.asList(validateExact(expected)), true, true,
                "collection to contain exactly the expected elements",
                "collection did not contain exactly the expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactly(E... expected) {
        return exact(Arrays.asList(validateExact(expected)), true, false,
                "collection not to contain exactly the expected elements",
                "collection contained exactly the expected elements");
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactlyElementsOf(Collection<? extends E> expected) {
        return exact(validateExact(expected), true, true,
                "collection to contain exactly the expected elements",
                "collection did not contain exactly the expected elements");
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactlyElementsOf(
                    Collection<? extends E> expected) {
        return exact(validateExact(expected), true, false,
                "collection not to contain exactly the expected elements",
                "collection contained exactly the expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrder(E... expected) {
        return exact(Arrays.asList(validateExact(expected)), false, true,
                "collection to contain exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrder(E... expected) {
        return exact(Arrays.asList(validateExact(expected)), false, false,
                "collection not to contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return exact(validateExact(expected), false, true,
                "collection to contain exactly the expected elements in any order",
                "collection did not contain exactly the expected elements in any order");
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return exact(validateExact(expected), false, false,
                "collection not to contain exactly the expected elements in any order",
                "collection contained exactly the expected elements in any order");
    }

    private static <E> PreservingCondition<Collection<? super E>> membership(
            Iterable<? extends E> expected, boolean all, boolean positive,
            String description, String mismatch) {
        return PreservingCondition.of(new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            boolean matches = all
                    ? allFound(actual, expected)
                    : anyMatch(actual, expected);
            return matches == positive
                    ? satisfied(actual)
                    : unsatisfied(mismatch);
        }, () -> description, null));
    }

    private static boolean anyMatch(Collection<?> actual,
            Iterable<?> expected) {
        for (Object actualElement : actual) {
            for (Object expectedElement : expected) {
                if (equal(actualElement, expectedElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allFound(Collection<?> actual,
            Iterable<?> expected) {
        List<Object> remaining = new ArrayList<>();
        for (Object element : expected) {
            remaining.add(element);
        }
        for (Object actualElement : actual) {
            remaining.removeIf(expectedElement ->
                    equal(actualElement, expectedElement));
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static <C extends Collection<?>> PreservingCondition<C> exact(
            Collection<?> expected, boolean ordered, boolean positive,
            String description, String mismatch) {
        return PreservingCondition.of(new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied("collection was null");
            }
            boolean matches = exactContent(actual, expected, ordered);
            return matches == positive
                    ? satisfied(actual)
                    : unsatisfied(mismatch);
        }, () -> description, null));
    }

    private static boolean exactContent(Collection<?> actual,
            Collection<?> expected, boolean ordered) {
        int actualSize = actual.size();
        int expectedSize = expected.size();
        if (actualSize != expectedSize) {
            return false;
        }
        if (actualSize == 0) {
            return true;
        }
        Iterator<?> actualIterator = actual.iterator();
        return ordered
                ? ordered(actualIterator, expected.iterator())
                : anyOrder(actualIterator, expected.iterator(), expectedSize);
    }

    private static boolean ordered(Iterator<?> actual, Iterator<?> expected) {
        while (actual.hasNext() && expected.hasNext()) {
            if (!equal(actual.next(), expected.next())) {
                return false;
            }
        }
        return !actual.hasNext() && !expected.hasNext();
    }

    private static boolean anyOrder(Iterator<?> actual, Iterator<?> expected,
            int expectedSize) {
        List<Object> remaining = new ArrayList<>(expectedSize);
        expected.forEachRemaining(remaining::add);
        int matched = 0;
        while (actual.hasNext()) {
            Object actualElement = actual.next();
            boolean found = false;
            Iterator<Object> candidates = remaining.iterator();
            while (candidates.hasNext()) {
                if (equal(actualElement, candidates.next())) {
                    candidates.remove();
                    matched++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return matched == expectedSize;
    }

    private static <E> E[] validate(E[] expected) {
        requireNonNull(expected, "expected elements must not be null");
        if (expected.length == 0) {
            throw new IllegalArgumentException(
                    "expected elements must not be empty");
        }
        return expected;
    }

    private static <E> Collection<? extends E> validate(
            Collection<? extends E> expected) {
        requireNonNull(expected, "expected elements must not be null");
        if (expected.isEmpty()) {
            throw new IllegalArgumentException(
                    "expected elements must not be empty");
        }
        return expected;
    }

    private static <T> T validateExact(T expected) {
        return requireNonNull(
                expected, "expected elements must not be null");
    }
}
