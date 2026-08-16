package io.github.gromoff97.awium.conditioning.providers;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {
        throw new AssertionError("Utility class");
    }

    static <T> boolean anyMatch(Iterable<T> values, Predicate<? super T> matches) {
        for (T value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <A, E> boolean containsAllMatches(Iterable<A> actual, Collection<E> remainingExpected,
            BiPredicate<? super A, ? super E> matches) {
        for (A value : actual) {
            remainingExpected.removeIf(expected -> matches.test(value, expected));
            if (remainingExpected.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static <A, E> boolean matchesExactly(Iterator<A> actual, Collection<E> remainingExpected,
            BiPredicate<? super A, ? super E> matches) {
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
        return true;
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
