package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preserving;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
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

    static <INPUT, ACTUAL, EXPECTED> PreservingCondition<INPUT> containingAll(
            String subject, String elements, List<EXPECTED> expected,
            Function<? super INPUT, ? extends Collection<? extends ACTUAL>> actualElements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        return preserving(subject + " contains all expected " + elements, expectedReference(expected), actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int missing = firstMissing(actualElements.apply(actual), expected, matches);
            return missing < 0 ? satisfied() : unsatisfied(subject + " did not contain all expected " + elements
                    + "; first missing at expected index " + missing);
        });
    }

    /** Returns the first missing expected index, or -1 when every operand is present. */
    static <ACTUAL, EXPECTED> int firstMissing(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        int index = 0;
        for (EXPECTED candidate : expected) {
            if (!matchesAny(actual, value -> matches.test(value, candidate))) {
                return index;
            }
            index++;
        }
        return -1;
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
