package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static java.util.Objects.deepEquals;

public final class ValueMatching {

    private ValueMatching() {}

    public static boolean prefixMatches(Iterator<?> actual, Iterable<?> expected) {
        for (Object value : expected) {
            if (!actual.hasNext() || !deepEquals(actual.next(), value)) {
                return false;
            }
        }
        return true;
    }

    public static <VALUE> boolean matchesAny(Iterable<? extends VALUE> values, CheckedPredicate<? super VALUE> matches) throws Throwable {
        for (VALUE value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    public static <VALUE> boolean matchesAll(Iterable<? extends VALUE> values, CheckedPredicate<? super VALUE> matches) throws Throwable {
        return !matchesAny(values, value -> !matches.test(value));
    }

    static <ACTUAL, EXPECTED> int firstMissingExpectedIndex(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        return indexOfFirstMatch(expected, candidate -> !matchesAny(actual, value -> matches.test(value, candidate)));
    }

    static <ACTUAL, EXPECTED> int firstPresentExpectedIndex(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        return indexOfFirstMatch(expected, candidate -> matchesAny(actual, value -> matches.test(value, candidate)));
    }

    private static <VALUE> int indexOfFirstMatch(Collection<VALUE> values, CheckedPredicate<? super VALUE> matches) throws Throwable {
        int index = 0;
        for (VALUE value : values) {
            if (matches.test(value)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static <ACTUAL, EXPECTED> boolean sameElementsInAnyOrder(Iterator<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            ACTUAL value = actual.next();
            int index = indexOfFirstMatch(remainingExpected, candidate -> matches.test(value, candidate));
            if (index < 0) {
                return false;
            }
            remainingExpected.remove(index);
        }
        return remainingExpected.isEmpty();
    }

    public static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Throwable {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> deepEquals(candidate, value)));
    }

}
