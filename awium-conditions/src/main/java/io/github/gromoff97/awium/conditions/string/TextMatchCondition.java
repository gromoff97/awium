package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.function.BiPredicate;

import static io.github.gromoff97.awium.conditions.common.ExpectedValues.text;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class TextMatchCondition extends PreservingCondition<CharSequence> {
    enum Match {
        CONTAINS("contains expected string", "did not contain expected string", "expected string", "Expected", (actual, value) -> actual.toString().contains(value)),
        DOES_NOT_CONTAIN("does not contain unexpected string", "contained unexpected string", "unexpected string", "Unexpected", (actual, value) -> !actual.toString().contains(value)),
        STARTS_WITH("starts with expected prefix", "did not start with expected prefix", "prefix", "Expected", (actual, value) -> actual.toString().startsWith(value)),
        DOES_NOT_START_WITH("does not start with unexpected prefix", "started with unexpected prefix", "prefix", "Unexpected", (actual, value) -> !actual.toString().startsWith(value)),
        ENDS_WITH("ends with expected suffix", "did not end with expected suffix", "suffix", "Expected", (actual, value) -> actual.toString().endsWith(value)),
        DOES_NOT_END_WITH("does not end with unexpected suffix", "ended with unexpected suffix", "suffix", "Unexpected", (actual, value) -> !actual.toString().endsWith(value)),
        CONTENT_EQUALS("content equals expected string", "content was not equal to expected string", "expected string", "Expected", (actual, value) -> value.contentEquals(actual)),
        EQUALS_IGNORING_CASE("equals expected string ignoring case", "was not equal to expected string ignoring case", "expected string", "Expected", (actual, value) -> actual.toString().equalsIgnoreCase(value)),
        NOT_EQUALS_IGNORING_CASE("does not equal unexpected string ignoring case", "was equal to unexpected string ignoring case", "unexpected string", "Unexpected", (actual, value) -> !actual.toString().equalsIgnoreCase(value));

        private final String description;
        private final String mismatch;
        private final String operand;
        private final String reference;
        private final BiPredicate<CharSequence, String> matches;

        Match(String description, String mismatch, String operand, String reference, BiPredicate<CharSequence, String> matches) {
            this.description = description;
            this.mismatch = mismatch;
            this.operand = operand;
            this.reference = reference;
            this.matches = matches;
        }
    }

    TextMatchCondition(CharSequence expected, Match match) {
        this(text(expected, match.operand), match);
    }

    private TextMatchCondition(String value, Match match) {
        super("string " + match.description, new Reference<>(match.reference, value), actual -> actual == null
                ? unsatisfied("string was null") : match.matches.test(actual, value) ? satisfied(actual) : unsatisfied("string " + match.mismatch));
    }

}
