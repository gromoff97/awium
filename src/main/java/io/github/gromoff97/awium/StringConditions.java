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
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * Text checks that preserve the original {@link CharSequence} type and instance.
 * A null observed text is unsatisfied, including for absence checks.
 * Expected text is snapshotted as a string when a condition is created.
 */
public final class StringConditions {

    public static final PreservingCondition<CharSequence> empty = matching("string is empty", "string was not empty", String::isEmpty);
    public static final PreservingCondition<CharSequence> nonEmpty = matching("string is not empty", "string was empty", value -> !value.isEmpty());
    public static final PreservingCondition<CharSequence> blank = matching("string is blank", "string was not blank", String::isBlank);
    public static final PreservingCondition<CharSequence> nonBlank = matching("string is not blank", "string was blank", value -> !value.isBlank());

    private StringConditions() {}

    public static PreservingCondition<CharSequence> contains(CharSequence expected) {
        String value = text(expected, "expected string");
        return matching("string contains expected string", "string did not contain expected string", expectedReference(value),
                actual -> actual.contains(value));
    }

    public static PreservingCondition<CharSequence> containsAll(Iterable<? extends CharSequence> expected) {
        List<String> values = texts(expected, "expected strings");
        return matching("string contains all expected strings", "string did not contain all expected strings", expectedReference(values),
                actual -> matchesAll(values, actual::contains));
    }

    public static PreservingCondition<CharSequence> doesNotContain(CharSequence unexpected) {
        String value = text(unexpected, "unexpected string");
        return matching("string does not contain unexpected string", "string contained unexpected string", unexpectedReference(value),
                actual -> !actual.contains(value));
    }

    public static PreservingCondition<CharSequence> doesNotContainAny(Iterable<? extends CharSequence> unexpected) {
        List<String> values = texts(unexpected, "unexpected strings");
        return matching("string does not contain unexpected strings", "string contained an unexpected string", unexpectedReference(values),
                actual -> !matchesAny(values, actual::contains));
    }

    public static PreservingCondition<CharSequence> containsIgnoringCase(CharSequence expected) {
        String value = text(expected, "expected string");
        var pattern = Pattern.compile(value, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return matching("string contains expected string ignoring case",
                "string did not contain expected string ignoring case", expectedReference(value),
                actual -> pattern.matcher(actual).find());
    }

    public static PreservingCondition<CharSequence> startsWith(CharSequence prefix) {
        String value = text(prefix, "prefix");
        return matching("string starts with expected prefix", "string did not start with expected prefix", expectedReference(value),
                actual -> actual.startsWith(value));
    }

    public static PreservingCondition<CharSequence> doesNotStartWith(CharSequence prefix) {
        String value = text(prefix, "prefix");
        return matching("string does not start with unexpected prefix", "string started with unexpected prefix", unexpectedReference(value),
                actual -> !actual.startsWith(value));
    }

    public static PreservingCondition<CharSequence> endsWith(CharSequence suffix) {
        String value = text(suffix, "suffix");
        return matching("string ends with expected suffix", "string did not end with expected suffix", expectedReference(value),
                actual -> actual.endsWith(value));
    }

    public static PreservingCondition<CharSequence> doesNotEndWith(CharSequence suffix) {
        String value = text(suffix, "suffix");
        return matching("string does not end with unexpected suffix", "string ended with unexpected suffix", unexpectedReference(value),
                actual -> !actual.endsWith(value));
    }

    public static PreservingCondition<CharSequence> matchesRegex(CharSequence regex) {
        return matchesRegex(Pattern.compile(text(regex, "regex")));
    }

    public static PreservingCondition<CharSequence> matchesRegex(Pattern expected) {
        requireNonNull(expected, "pattern must not be null");
        return matching("string matches expected pattern", "string did not match expected pattern", expectedReference(expected),
                actual -> expected.matcher(actual).matches());
    }

    public static PreservingCondition<CharSequence> doesNotMatchRegex(CharSequence regex) {
        return doesNotMatchRegex(Pattern.compile(text(regex, "regex")));
    }

    public static PreservingCondition<CharSequence> doesNotMatchRegex(Pattern unexpected) {
        requireNonNull(unexpected, "pattern must not be null");
        return matching("string does not match unexpected pattern", "string matched unexpected pattern", unexpectedReference(unexpected),
                actual -> !unexpected.matcher(actual).matches());
    }

    /** Compares characters, including case, rather than the source object's equality contract. */
    public static PreservingCondition<CharSequence> contentEquals(CharSequence expected) {
        String value = text(expected, "expected string");
        return matching("string content equals expected string",
                "string content was not equal to expected string", expectedReference(value), actual -> actual.equals(value));
    }

    public static PreservingCondition<CharSequence> equalToIgnoringCase(CharSequence expected) {
        String value = text(expected, "expected string");
        return matching("string equals expected string ignoring case",
                "string was not equal to expected string ignoring case", expectedReference(value), actual -> actual.equalsIgnoreCase(value));
    }

    public static PreservingCondition<CharSequence> notEqualToIgnoringCase(CharSequence unexpected) {
        String value = text(unexpected, "unexpected string");
        return matching("string does not equal unexpected string ignoring case",
                "string was equal to unexpected string ignoring case", unexpectedReference(value), actual -> !actual.equalsIgnoreCase(value));
    }

    public static PreservingCondition<CharSequence> hasLength(int expected) {
        return sized(expected, actual -> actual == expected, "is " + expected);
    }

    public static PreservingCondition<CharSequence> hasLengthNotEqualTo(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected, "is not " + unexpected);
    }

    public static PreservingCondition<CharSequence> hasLengthGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound);
    }

    public static PreservingCondition<CharSequence> hasLengthAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound);
    }

    public static PreservingCondition<CharSequence> hasLengthLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound, "is less than " + upperBound);
    }

    public static PreservingCondition<CharSequence> hasLengthAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound, "is at most " + upperBound);
    }

    public static PreservingCondition<CharSequence> hasLengthBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "length");
        return sized(lowerBound, length -> length >= lowerBound && length <= upperBound,
                "is between " + lowerBound + " and " + upperBound);
    }

    private static PreservingCondition<CharSequence> sized(int bound, IntPredicate matches,
            String relation) {
        return ConditionSupport.sized("string", "length", bound, matches, relation, CharSequence::length);
    }

    private static PreservingCondition<CharSequence> matching(String description, String mismatch,
            CheckedPredicate<String> matches) {
        return preservingNonNull("string", description, mismatch, actual -> matches.test(actual.toString()));
    }

    private static PreservingCondition<CharSequence> matching(String description, String mismatch,
            Reference<?> reference, CheckedPredicate<String> matches) {
        return preservingNonNull("string", description, mismatch, reference, actual -> matches.test(actual.toString()));
    }

    private static List<String> texts(Iterable<? extends CharSequence> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false)
                .map(value -> text(value, "text")).toList();
    }

    private static String text(CharSequence value, String name) {
        return requireNonNull(requireNonNull(value, name + " must not be null").toString(),
                "text.toString() must not return null");
    }
}
