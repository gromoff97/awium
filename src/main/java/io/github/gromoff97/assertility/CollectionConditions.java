package io.github.gromoff97.assertility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class CollectionConditions {

    private CollectionConditions() {
    }

    static <E> PreservingCondition<Collection<? super E>> contains(E expected) {
        return membership(Collections.singletonList(expected), false, true,
                "collection to contain expected element",
                "collection did not contain expected element");
    }

    static <E> PreservingCondition<Collection<? super E>> doesNotContain(
            E expected) {
        return membership(Collections.singletonList(expected), false, false,
                "collection not to contain expected element",
                "collection contained expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> PreservingCondition<Collection<? super E>> containsAll(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), true, true,
                "collection to contain all expected elements",
                "collection did not contain all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> PreservingCondition<Collection<? super E>> doesNotContainAll(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), true, false,
                "collection not to contain all expected elements",
                "collection contained all expected elements");
    }

    static <E> PreservingCondition<Collection<? super E>>
            containsAllElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), true, true,
                "collection to contain all expected elements",
                "collection did not contain all expected elements");
    }

    static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAllElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), true, false,
                "collection not to contain all expected elements",
                "collection contained all expected elements");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> PreservingCondition<Collection<? super E>> containsAnyOf(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), false, true,
                "collection to contain any expected element",
                "collection did not contain any expected element");
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> PreservingCondition<Collection<? super E>> containsNoneOf(
            E... expected) {
        return membership(Arrays.asList(validate(expected)), false, false,
                "collection to contain none of the expected elements",
                "collection contained an expected element");
    }

    static <E> PreservingCondition<Collection<? super E>>
            containsAnyElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), false, true,
                "collection to contain any expected element",
                "collection did not contain any expected element");
    }

    static <E> PreservingCondition<Collection<? super E>>
            containsNoElementsOf(Collection<? extends E> expected) {
        return membership(validate(expected), false, false,
                "collection to contain none of the expected elements",
                "collection contained an expected element");
    }

    private static <E> PreservingCondition<Collection<? super E>> membership(
            Iterable<? extends E> expected, boolean all, boolean positive,
            String description, String mismatch) {
        ConditionRuntime<Collection<? super E>, Collection<? super E>> runtime =
                new ConditionRuntime<>(actual -> {
                    if (actual == null) {
                        return Evaluation.unsatisfied("collection was null");
                    }
                    boolean matches = all
                            ? allFound(actual, expected)
                            : anyMatch(actual, expected);
                    return matches == positive
                            ? Evaluation.satisfied(actual)
                            : Evaluation.unsatisfied(mismatch);
                }, () -> description, null);
        return new PreservingCondition<>(runtime);
    }

    private static boolean anyMatch(Collection<?> actual,
            Iterable<?> expected) {
        for (Object actualElement : actual) {
            for (Object expectedElement : expected) {
                if (ValueEquality.equal(actualElement, expectedElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allFound(Collection<?> actual,
            Iterable<?> expected) {
        List<Object> positions = new ArrayList<>();
        for (Object element : expected) {
            positions.add(element);
        }
        boolean[] found = new boolean[positions.size()];
        int remaining = found.length;
        for (Object actualElement : actual) {
            for (int index = 0; index < positions.size(); index++) {
                if (!found[index] && ValueEquality.equal(
                        actualElement, positions.get(index))) {
                    found[index] = true;
                    if (--remaining == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static <E> E[] validate(E[] expected) {
        Objects.requireNonNull(expected, "expected elements must not be null");
        if (expected.length == 0) {
            throw new IllegalArgumentException(
                    "expected elements must not be empty");
        }
        return expected;
    }

    private static <E> Collection<? extends E> validate(
            Collection<? extends E> expected) {
        Objects.requireNonNull(expected, "expected elements must not be null");
        if (expected.isEmpty()) {
            throw new IllegalArgumentException(
                    "expected elements must not be empty");
        }
        return expected;
    }
}
