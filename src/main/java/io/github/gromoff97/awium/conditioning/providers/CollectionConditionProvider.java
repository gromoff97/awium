package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.containsAllMatches;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.anyMatch;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.matchesExactly;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.matchingCondition;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public final class CollectionConditionProvider {

    private CollectionConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <E> PreservingCondition<Collection<? super E>> contains(E expected) {
        return membership(singletonList(expected), false, true, "expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(E expected) {
        return membership(singletonList(expected), false, false, "expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAll(E... expected) {
        return membership(asList(validateArray(expected)), true, true, "all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(E... expected) {
        return membership(asList(validateArray(expected)), true, false, "all expected elements");
    }

    public static <E> PreservingCondition<Collection<? super E>> containsAllElementsOf(Collection<? extends E> expected) {
        return membership(expected, true, true, "all expected elements");
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainAllElementsOf(Collection<? extends E> expected) {
        return membership(expected, true, false, "all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAnyOf(E... expected) {
        return membership(asList(validateArray(expected)), false, true, "any expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsNoneOf(E... expected) {
        return membership(asList(validateArray(expected)), false, false, "any expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>> containsAnyElementsOf(Collection<? extends E> expected) {
        return membership(expected, false, true, "any expected element");
    }

    public static <E> PreservingCondition<Collection<? super E>> containsNoElementsOf(Collection<? extends E> expected) {
        return membership(expected, false, false, "any expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactly(E... expected) {
        return exact(asList(validateArray(expected)), true, true);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactly(E... expected) {
        return exact(asList(validateArray(expected)), true, false);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> containsExactlyElementsOf(Collection<? extends E> expected) {
        return exact(expected, true, true);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>> doesNotContainExactlyElementsOf(Collection<? extends E> expected) {
        return exact(expected, true, false);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrder(E... expected) {
        return exact(asList(validateArray(expected)), false, true);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrder(E... expected) {
        return exact(asList(validateArray(expected)), false, false);
    }

    public static <E> PreservingCondition<Collection<? super E>> containsExactlyInAnyOrderElementsOf(Collection<? extends E> expected) {
        return exact(expected, false, true);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContainExactlyInAnyOrderElementsOf(Collection<? extends E> expected) {
        return exact(expected, false, false);
    }

    private static <E> PreservingCondition<Collection<? super E>> membership(Collection<? extends E> expected, boolean all,
            boolean positive, String target) {
        if (requireNonNull(expected, "expected elements must not be null").isEmpty()) {
            throw new IllegalArgumentException("expected elements must not be empty");
        }
        String description = "collection " + (positive ? "contains " : "does not contain ") + target;
        String mismatch = "collection " + (positive ? "did not contain " : "contained ")
                + (positive || all ? target : "an expected element");
        return matchingCondition("collection", description, mismatch, positive, actual -> all
                ? containsAllMatches(actual, new ArrayList<>(expected), ConditionProvider::equal)
                : anyMatch(actual, value -> anyMatch(expected, candidate -> equal(value, candidate))));
    }

    private static <C extends Collection<?>> PreservingCondition<C> exact(Collection<?> expected, boolean ordered, boolean positive) {
        requireNonNull(expected, "expected elements must not be null");
        String order = ordered ? "" : " in any order";
        String description = "collection "
                + (positive ? "contains " : "does not contain ")
                + "exactly the expected elements" + order;
        String mismatch = "collection "
                + (positive ? "did not contain" : "contained")
                + " exactly the expected elements" + order;
        return matchingCondition("collection", description, mismatch, positive, actual -> exactContent(actual, expected, ordered));
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected, boolean ordered) {
        int actualSize = actual.size();
        if (actualSize != expected.size()) {
            return false;
        }
        return actualSize == 0 || (ordered ? ordered(actual.iterator(), expected.iterator()) : anyOrder(actual.iterator(), expected.iterator()));
    }

    private static boolean ordered(Iterator<?> actual, Iterator<?> expected) {
        while (actual.hasNext() && expected.hasNext()) {
            if (!equal(actual.next(), expected.next())) {
                return false;
            }
        }
        return !actual.hasNext() && !expected.hasNext();
    }

    private static boolean anyOrder(Iterator<?> actual, Iterator<?> expected) {
        List<Object> remaining = new ArrayList<>();
        expected.forEachRemaining(remaining::add);
        return matchesExactly(actual, remaining, ConditionProvider::equal);
    }

    private static <E> E[] validateArray(E[] expected) {
        return requireNonNull(expected, "expected elements must not be null");
    }
}
