package io.github.gromoff97.awium.fluent;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    static <Value> boolean matchesAny(Iterable<Value> values, Predicate<? super Value> matches) {
        for (Value value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <Value> boolean matchesAll(Iterable<Value> values, Predicate<? super Value> matches) {
        for (Value value : values) {
            if (!matches.test(value)) {
                return false;
            }
        }
        return true;
    }

    static <Actual, Expected> boolean containsAll(Iterable<Actual> actual, Collection<Expected> expected,
            BiPredicate<? super Actual, ? super Expected> matches) {
        var remainingExpected = new ArrayList<>(expected);
        for (Actual value : actual) {
            Iterator<Expected> candidates = remainingExpected.iterator();
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

    static <Actual, Expected> boolean exactly(Iterator<Actual> actual, Collection<Expected> expected,
            BiPredicate<? super Actual, ? super Expected> matches) {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            Actual value = actual.next();
            boolean found = false;
            Iterator<Expected> candidates = remainingExpected.iterator();
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

    static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> equal(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> equal(candidate, value)));
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
