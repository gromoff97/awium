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
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
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
            int missingIndex = firstMissingExpectedIndex(actualElements.apply(actual), expected, matches);
            return missingIndex < 0 ? satisfied(null) : unsatisfied(subject + " did not contain all expected " + elements
                    + "; first missing at expected index " + missingIndex);
        });
    }

    static <ACTUAL, EXPECTED> int firstMissingExpectedIndex(Collection<ACTUAL> actual, Collection<EXPECTED> expected,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        return indexOfFirstMatch(expected, candidate -> !matchesAny(actual, value -> matches.test(value, candidate)));
    }

    static <INPUT, ACTUAL, UNEXPECTED> PreservingCondition<INPUT> containingNone(
            String subject, String elements, List<UNEXPECTED> unexpected,
            Function<? super INPUT, ? extends Collection<? extends ACTUAL>> actualElements,
            CheckedBiPredicate<? super ACTUAL, ? super UNEXPECTED> matches) {
        return preserving(subject + " does not contain any unexpected " + elements, unexpectedReference(unexpected), actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int presentIndex = firstPresentExpectedIndex(actualElements.apply(actual), unexpected, matches);
            return presentIndex < 0 ? satisfied(null) : unsatisfied(subject + " contained unexpected " + elements
                    + "; first at unexpected index " + presentIndex);
        });
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

    static <ACTUAL, EXPECTED> boolean sameElementsInAnyOrder(Iterator<ACTUAL> actual, Collection<EXPECTED> expected,
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

    static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Throwable {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> deepEquals(candidate, value)));
    }

}
