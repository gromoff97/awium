package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedBiPredicate;
import io.github.gromoff97.awium.conditioning.CheckedPredicate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {
        throw new AssertionError("Utility class");
    }

    static <T> boolean matchesAny(Iterable<T> values, CheckedPredicate<? super T> matches) throws Exception {
        for (T value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <T> boolean matchesAll(Iterable<T> values, CheckedPredicate<? super T> matches) throws Exception {
        for (T value : values) {
            if (!matches.test(value)) {
                return false;
            }
        }
        return true;
    }

    static <A, E> boolean containsAll(Iterable<A> actual, Collection<E> expected,
            CheckedBiPredicate<? super A, ? super E> matches) throws Exception {
        var remainingExpected = new ArrayList<>(expected);
        for (A value : actual) {
            Iterator<E> candidates = remainingExpected.iterator();
            while (candidates.hasNext()) {
                if (matches.test(value, candidates.next())) {
                    candidates.remove();
                }
            }
            if (remainingExpected.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static <A, E> boolean exactly(Iterator<A> actual, Collection<E> expected,
            CheckedBiPredicate<? super A, ? super E> matches) throws Exception {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            A value = actual.next();
            boolean found = false;
            Iterator<E> candidates = remainingExpected.iterator();
            while (candidates.hasNext()) {
                if (matches.test(value, candidates.next())) {
                    candidates.remove();
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return remainingExpected.isEmpty();
    }

    static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Exception {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> equal(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> equal(value, candidate)));
    }

    static boolean equal(Object actual, Object expected) {
        record ValuePair(Object actual, Object expected) {}

        var pending = new ArrayDeque<ValuePair>();
        var visited = new HashSet<ValuePair>();
        pending.addLast(new ValuePair(actual, expected));
        while (!pending.isEmpty()) {
            ValuePair pair = pending.removeLast();
            Object left = pair.actual();
            Object right = pair.expected();
            if (left == right) {
                continue;
            }
            if (left instanceof Object[] leftObjects && right instanceof Object[] rightObjects) {
                if (!visited.add(pair)) {
                    continue;
                }
                if (leftObjects.length != rightObjects.length) {
                    return false;
                }
                for (int index = leftObjects.length - 1; index >= 0; index--) {
                    pending.addLast(new ValuePair(leftObjects[index], rightObjects[index]));
                }
            } else if (!deepEquals(left, right)) {
                return false;
            }
        }
        return true;
    }
}
