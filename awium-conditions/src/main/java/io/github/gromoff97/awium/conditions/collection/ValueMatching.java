package io.github.gromoff97.awium.conditions.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {}

    static boolean prefixMatches(Iterator<?> actual, Iterable<?> expected) {
        for (Object value : expected) {
            if (!actual.hasNext() || !deepEquals(actual.next(), value)) {
                return false;
            }
        }
        return true;
    }

    static <VALUE> boolean matchesAny(Iterable<? extends VALUE> values, Predicate<? super VALUE> matches) {
        for (VALUE value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <VALUE> boolean matchesAll(Iterable<? extends VALUE> values, Predicate<? super VALUE> matches) {
        return !matchesAny(values, value -> !matches.test(value));
    }

    static int firstMissingExpectedIndex(Collection<?> actual, Collection<?> expected) {
        return indexOfFirstMatch(expected, candidate -> !matchesAny(actual, value -> deepEquals(value, candidate)));
    }

    static int firstPresentExpectedIndex(Collection<?> actual, Collection<?> expected) {
        return indexOfFirstMatch(expected, candidate -> matchesAny(actual, value -> deepEquals(value, candidate)));
    }

    private static <VALUE> int indexOfFirstMatch(Collection<VALUE> values, Predicate<? super VALUE> matches) {
        int index = 0;
        for (VALUE value : values) {
            if (matches.test(value)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static boolean sameElementsInAnyOrder(Iterator<?> actual, Collection<?> expected) {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            Object value = actual.next();
            int index = indexOfFirstMatch(remainingExpected, candidate -> deepEquals(value, candidate));
            if (index < 0) {
                return false;
            }
            remainingExpected.remove(index);
        }
        return remainingExpected.isEmpty();
    }

    static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> deepEquals(candidate, value)));
    }
}
