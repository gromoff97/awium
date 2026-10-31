package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.conditions.internal.TextMatchCondition.text;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.regex.Pattern.compile;

public final class ContainsIgnoringCaseCondition extends PreservingCondition<CharSequence> {
    public ContainsIgnoringCaseCondition(CharSequence expected) {
        this(text(expected, "expected string"));
    }

    private ContainsIgnoringCaseCondition(String expected) {
        this(expected, compile(expected, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private ContainsIgnoringCaseCondition(String expected, Pattern pattern) {
        super("string contains expected string ignoring case", new Reference<>("Expected", expected), actual -> actual == null
                ? unsatisfied("string was null") : pattern.matcher(actual).find() ? satisfied(actual)
                        : unsatisfied("string did not contain expected string ignoring case"));
    }

}
