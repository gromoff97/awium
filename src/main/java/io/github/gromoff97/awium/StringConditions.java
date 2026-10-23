package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static java.util.Objects.requireNonNull;

public final class StringConditions {

    public static final PreservingCondition<String> empty = matching("string is empty", "string was not empty", String::isEmpty);
    public static final PreservingCondition<String> nonEmpty = matching("string is not empty", "string was empty", value -> !value.isEmpty());
    public static final PreservingCondition<String> blank = matching("string is blank", "string was not blank", String::isBlank);
    public static final PreservingCondition<String> nonBlank = matching("string is not blank", "string was blank", value -> !value.isBlank());

    private StringConditions() {}

    public static PreservingCondition<String> contains(String... expected) {
        List<String> values = nonEmpty(expected, "expected strings");
        return matching("string contains all expected strings", "string did not contain all expected strings", expectedReference(values),
                actual -> values.stream().allMatch(actual::contains));
    }

    public static PreservingCondition<String> doesNotContain(String... unexpected) {
        List<String> values = nonEmpty(unexpected, "unexpected strings");
        return matching("string does not contain unexpected strings", "string contained an unexpected string", unexpectedReference(values),
                actual -> values.stream().noneMatch(actual::contains));
    }

    public static PreservingCondition<String> containsIgnoringCase(String expected) {
        requireNonNull(expected, "expected string must not be null");
        return matching("string contains expected string ignoring case",
                "string did not contain expected string ignoring case", expectedReference(expected),
                actual -> containsIgnoringCase(actual, expected));
    }

    public static PreservingCondition<String> startsWith(String prefix) {
        requireNonNull(prefix, "prefix must not be null");
        return matching("string starts with expected prefix", "string did not start with expected prefix", expectedReference(prefix),
                actual -> actual.startsWith(prefix));
    }

    public static PreservingCondition<String> doesNotStartWith(String prefix) {
        requireNonNull(prefix, "prefix must not be null");
        return matching("string does not start with unexpected prefix", "string started with unexpected prefix", unexpectedReference(prefix),
                actual -> !actual.startsWith(prefix));
    }

    public static PreservingCondition<String> endsWith(String suffix) {
        requireNonNull(suffix, "suffix must not be null");
        return matching("string ends with expected suffix", "string did not end with expected suffix", expectedReference(suffix),
                actual -> actual.endsWith(suffix));
    }

    public static PreservingCondition<String> doesNotEndWith(String suffix) {
        requireNonNull(suffix, "suffix must not be null");
        return matching("string does not end with unexpected suffix", "string ended with unexpected suffix", unexpectedReference(suffix),
                actual -> !actual.endsWith(suffix));
    }

    public static PreservingCondition<String> matchesRegex(String regex) {
        return matchesRegex(Pattern.compile(requireNonNull(regex, "regex must not be null")));
    }

    public static PreservingCondition<String> matchesRegex(Pattern expected) {
        requireNonNull(expected, "pattern must not be null");
        return matching("string matches expected pattern", "string did not match expected pattern", expectedReference(expected),
                actual -> expected.matcher(actual).matches());
    }

    public static PreservingCondition<String> doesNotMatchRegex(String regex) {
        return doesNotMatchRegex(Pattern.compile(requireNonNull(regex, "regex must not be null")));
    }

    public static PreservingCondition<String> doesNotMatchRegex(Pattern unexpected) {
        requireNonNull(unexpected, "pattern must not be null");
        return matching("string does not match unexpected pattern", "string matched unexpected pattern", unexpectedReference(unexpected),
                actual -> !unexpected.matcher(actual).matches());
    }

    public static PreservingCondition<String> equalToIgnoringCase(String expected) {
        requireNonNull(expected, "expected string must not be null");
        return matching("string equals expected string ignoring case",
                "string was not equal to expected string ignoring case", expectedReference(expected), actual -> actual.equalsIgnoreCase(expected));
    }

    public static PreservingCondition<String> notEqualToIgnoringCase(String unexpected) {
        requireNonNull(unexpected, "unexpected string must not be null");
        return matching("string does not equal unexpected string ignoring case",
                "string was equal to unexpected string ignoring case", unexpectedReference(unexpected), actual -> !actual.equalsIgnoreCase(unexpected));
    }

    public static PreservingCondition<String> length(int expected) {
        return matchingLength(expected, actual -> actual == expected, "is " + expected);
    }

    public static PreservingCondition<String> lengthIsNot(int unexpected) {
        return matchingLength(unexpected, actual -> actual != unexpected, "is not " + unexpected);
    }

    public static PreservingCondition<String> lengthGreaterThan(int lowerBound) {
        return matchingLength(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound);
    }

    public static PreservingCondition<String> lengthAtLeast(int lowerBound) {
        return matchingLength(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound);
    }

    public static PreservingCondition<String> lengthLessThan(int upperBound) {
        return matchingLength(upperBound, actual -> actual < upperBound, "is less than " + upperBound);
    }

    public static PreservingCondition<String> lengthAtMost(int upperBound) {
        return matchingLength(upperBound, actual -> actual <= upperBound, "is at most " + upperBound);
    }

    public static PreservingCondition<String> lengthBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "length");
        return matchingLength(lowerBound, length -> length >= lowerBound && length <= upperBound,
                "is between " + lowerBound + " and " + upperBound);
    }

    private static PreservingCondition<String> matchingLength(int bound, IntPredicate matches,
            String relation) {
        return ConditionSupport.sized("string", "length", bound, matches, "string length " + relation, String::length);
    }

    private static PreservingCondition<String> matching(String description, String mismatch,
            CheckedFunction<String, Boolean> matches) {
        return preservingNonNull("string", description, mismatch, matches);
    }

    private static PreservingCondition<String> matching(String description, String mismatch,
            Reference<?> reference, CheckedFunction<String, Boolean> matches) {
        return preservingNonNull("string", description, mismatch, reference, matches);
    }

    private static boolean containsIgnoringCase(String actual, String expected) {
        for (int index = 0; index <= actual.length() - expected.length(); index++) {
            if (actual.regionMatches(true, index, expected, 0, expected.length())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nonEmpty(String[] values, String name) {
        ConditionSupport.nonEmpty(values, name);
        for (String value : values) {
            requireNonNull(value, name + " must not contain null");
        }
        return List.of(values);
    }
}
