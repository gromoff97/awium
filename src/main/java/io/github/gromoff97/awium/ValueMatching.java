package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {}

    static <VALUE> boolean matchesAny(Iterable<VALUE> values, Predicate<? super VALUE> matches) {
        for (VALUE value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <VALUE> boolean matchesAll(Iterable<VALUE> values, Predicate<? super VALUE> matches) {
        return !matchesAny(values, value -> !matches.test(value));
    }

    static <ACTUAL, EXPECTED> boolean containsAll(Iterable<ACTUAL> actual, Collection<EXPECTED> expected,
            BiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        return matchesAll(expected, candidate -> matchesAny(actual, value -> matches.test(value, candidate)));
    }

    static <ACTUAL, EXPECTED> boolean exactly(Iterator<ACTUAL> actual, Collection<EXPECTED> expected,
            BiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            ACTUAL value = actual.next();
            boolean found = false;
            Iterator<EXPECTED> candidates = remainingExpected.iterator();
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
        return matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> deepEquals(candidate, value)));
    }

}
