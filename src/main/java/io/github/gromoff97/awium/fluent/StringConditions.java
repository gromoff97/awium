package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.fluent.Condition.PreservingCondition;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.fluent.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.fluent.ConditionSupport.validateRange;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

public final class StringConditions {

    public static final PreservingCondition<String> empty = matching("string is empty", "string was not empty", String::isEmpty);
    public static final PreservingCondition<String> nonEmpty = matching("string is not empty", "string was empty", value -> !value.isEmpty());
    public static final PreservingCondition<String> blank = matching("string is blank", "string was not blank", String::isBlank);
    public static final PreservingCondition<String> nonBlank = matching("string is not blank", "string was blank", value -> !value.isBlank());

    private StringConditions() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<String> contains(String... expected) {
        String[] values = nonEmpty(expected, "expected strings");
        return matching("string contains all expected strings", "string did not contain all expected strings",
                actual -> stream(values).allMatch(actual::contains));
    }

    public static PreservingCondition<String> doesNotContain(String... unexpected) {
        String[] values = nonEmpty(unexpected, "unexpected strings");
        return matching("string does not contain unexpected strings", "string contained an unexpected string",
                actual -> stream(values).noneMatch(actual::contains));
    }

    public static PreservingCondition<String> containsIgnoringCase(String expected) {
        String fragment = requireNonNull(expected, "expected string must not be null").toLowerCase(Locale.ROOT);
        return matching("string contains expected string ignoring case",
                "string did not contain expected string ignoring case",
                actual -> actual.toLowerCase(Locale.ROOT).contains(fragment));
    }

    public static PreservingCondition<String> startsWith(String prefix) {
        requireNonNull(prefix, "prefix must not be null");
        return matching("string starts with expected prefix", "string did not start with expected prefix",
                actual -> actual.startsWith(prefix));
    }

    public static PreservingCondition<String> doesNotStartWith(String prefix) {
        requireNonNull(prefix, "prefix must not be null");
        return matching("string does not start with unexpected prefix", "string started with unexpected prefix",
                actual -> !actual.startsWith(prefix));
    }

    public static PreservingCondition<String> endsWith(String suffix) {
        requireNonNull(suffix, "suffix must not be null");
        return matching("string ends with expected suffix", "string did not end with expected suffix",
                actual -> actual.endsWith(suffix));
    }

    public static PreservingCondition<String> doesNotEndWith(String suffix) {
        requireNonNull(suffix, "suffix must not be null");
        return matching("string does not end with unexpected suffix", "string ended with unexpected suffix",
                actual -> !actual.endsWith(suffix));
    }

    public static PreservingCondition<String> matchesRegex(String regex) {
        return matchesRegex(Pattern.compile(requireNonNull(regex, "regex must not be null")));
    }

    public static PreservingCondition<String> matchesRegex(Pattern pattern) {
        Pattern expected = requireNonNull(pattern, "pattern must not be null");
        return matching("string matches expected pattern", "string did not match expected pattern",
                actual -> expected.matcher(actual).matches());
    }

    public static PreservingCondition<String> doesNotMatchRegex(String regex) {
        return doesNotMatchRegex(Pattern.compile(requireNonNull(regex, "regex must not be null")));
    }

    public static PreservingCondition<String> doesNotMatchRegex(Pattern pattern) {
        Pattern unexpected = requireNonNull(pattern, "pattern must not be null");
        return matching("string does not match unexpected pattern", "string matched unexpected pattern",
                actual -> !unexpected.matcher(actual).matches());
    }

    public static PreservingCondition<String> equalToIgnoringCase(String expected) {
        requireNonNull(expected, "expected string must not be null");
        return matching("string equals expected string ignoring case",
                "string was not equal to expected string ignoring case", actual -> actual.equalsIgnoreCase(expected));
    }

    public static PreservingCondition<String> notEqualToIgnoringCase(String unexpected) {
        requireNonNull(unexpected, "unexpected string must not be null");
        return matching("string does not equal unexpected string ignoring case",
                "string was equal to unexpected string ignoring case", actual -> !actual.equalsIgnoreCase(unexpected));
    }

    public static PreservingCondition<String> length(int expected) {
        return matchingLength(expected, actual -> actual == expected, "is " + expected, "was not " + expected);
    }

    public static PreservingCondition<String> lengthIsNot(int unexpected) {
        return matchingLength(unexpected, actual -> actual != unexpected, "is not " + unexpected, "was " + unexpected);
    }

    public static PreservingCondition<String> lengthGreaterThan(int lowerBound) {
        return matchingLength(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound,
                "was not greater than " + lowerBound);
    }

    public static PreservingCondition<String> lengthAtLeast(int lowerBound) {
        return matchingLength(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound,
                "was less than " + lowerBound);
    }

    public static PreservingCondition<String> lengthLessThan(int upperBound) {
        return matchingLength(upperBound, actual -> actual < upperBound, "is less than " + upperBound,
                "was not less than " + upperBound);
    }

    public static PreservingCondition<String> lengthAtMost(int upperBound) {
        return matchingLength(upperBound, actual -> actual <= upperBound, "is at most " + upperBound,
                "was greater than " + upperBound);
    }

    public static PreservingCondition<String> lengthBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "length");
        return matching("string length is between " + lowerBound + " and " + upperBound,
                "string length was outside " + lowerBound + ".." + upperBound,
                actual -> actual.length() >= lowerBound && actual.length() <= upperBound);
    }

    private static PreservingCondition<String> matchingLength(int bound, java.util.function.IntPredicate matches,
            String relation, String mismatch) {
        if (bound < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        return matching("string length " + relation, "string length " + mismatch,
                actual -> matches.test(actual.length()));
    }

    private static PreservingCondition<String> matching(String description, String mismatch,
            Predicate<String> matches) {
        return preservingNonNull("string", description, mismatch, matches);
    }

    private static String[] nonEmpty(String[] values, String name) {
        ConditionSupport.nonEmpty(values, name);
        for (String value : values) {
            requireNonNull(value, name + " must not contain null");
        }
        return values;
    }
}
