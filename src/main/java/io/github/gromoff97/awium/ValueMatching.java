package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static java.util.Objects.deepEquals;

final class ValueMatching {

    private ValueMatching() {}

    static <VALUE> boolean matchesAny(Iterable<VALUE> values, CheckedPredicate<? super VALUE> matches) throws Throwable {
        for (VALUE value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <VALUE> boolean matchesAll(Iterable<VALUE> values, CheckedPredicate<? super VALUE> matches) throws Throwable {
        return !matchesAny(values, value -> !matches.test(value));
    }

    static <ACTUAL, EXPECTED> boolean containsAll(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        return matchesAll(expected, candidate -> matchesAny(actual, value -> matches.test(value, candidate)));
    }

    static <ACTUAL, EXPECTED> boolean containsAny(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        return matchesAny(expected, candidate -> matchesAny(actual, value -> matches.test(value, candidate)));
    }

    static <ACTUAL, EXPECTED> boolean exactly(Iterator<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        var remainingExpected = new ArrayList<>(expected);
        while (actual.hasNext()) {
            ACTUAL value = actual.next();
            int index = 0;
            while (index < remainingExpected.size() && !matches.test(value, remainingExpected.get(index))) {
                index++;
            }
            if (index == remainingExpected.size()) {
                return false;
            }
            remainingExpected.remove(index);
        }
        return remainingExpected.isEmpty();
    }

    static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Throwable {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> deepEquals(candidate, value)));
    }

}
