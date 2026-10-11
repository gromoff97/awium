package io.github.gromoff97.awium;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static io.github.gromoff97.awium.CheckedCalls.call;
import static io.github.gromoff97.awium.CheckedCalls.checkInterruption;
import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {}

    @FunctionalInterface
    interface CheckedBiPredicate<Actual, Expected> {
        boolean test(Actual actual, Expected expected) throws Exception;
    }

    static <Value> boolean matchesAny(Iterable<Value> values, CheckedFunction<? super Value, Boolean> matches) throws Exception {
        for (var iterator = call(values::iterator); call(iterator::hasNext);) {
            Value value = call(iterator::next);
            boolean matching = call(() -> matches.apply(value));
            if (matching) {
                return true;
            }
        }
        return false;
    }

    static <Value> boolean matchesAll(Iterable<Value> values, CheckedFunction<? super Value, Boolean> matches) throws Exception {
        return !matchesAny(values, value -> !matches.apply(value));
    }

    static <Actual, Expected> boolean containsAll(Iterable<Actual> actual, Collection<Expected> expected,
            CheckedBiPredicate<? super Actual, ? super Expected> matches) throws Exception {
        var remainingExpected = new ArrayList<>(expected);
        if (remainingExpected.isEmpty()) {
            return true;
        }
        for (var iterator = call(actual::iterator); call(iterator::hasNext);) {
            Actual value = call(iterator::next);
            Iterator<Expected> candidates = remainingExpected.iterator();
            while (candidates.hasNext()) {
                if (call(() -> matches.test(value, candidates.next()))) {
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
            CheckedBiPredicate<? super Actual, ? super Expected> matches) throws Exception {
        var remainingExpected = new ArrayList<>(expected);
        while (call(actual::hasNext)) {
            Actual value = call(actual::next);
            boolean found = false;
            Iterator<Expected> candidates = remainingExpected.iterator();
            while (candidates.hasNext()) {
                if (call(() -> matches.test(value, candidates.next()))) {
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
                && matchesAll(expected, value -> matchesAny(actual, candidate -> equal(candidate, value)));
    }

    static boolean equal(Object actual, Object expected) throws InterruptedException {
        record ValuePair(Object actual, Object expected) {}

        var pending = new ArrayDeque<ValuePair>();
        var visited = new HashSet<ValuePair>();
        pending.addLast(new ValuePair(actual, expected));
        while (!pending.isEmpty()) {
            checkInterruption();
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
            } else {
                boolean matching = deepEquals(left, right);
                checkInterruption();
                if (!matching) {
                    return false;
                }
            }
        }
        return true;
    }
}
