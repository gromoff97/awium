package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.support.ExpectedValues.text;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.compile;

final class RegexCondition extends PreservingCondition<CharSequence> {
    RegexCondition(CharSequence regex, boolean negated) {
        this(compile(text(regex, "regex")), negated);
    }

    RegexCondition(Pattern pattern, boolean negated) {
        super(negated ? "string does not match unexpected pattern" : "string matches expected pattern",
                new Reference<>(negated ? "Unexpected" : "Expected", requireNonNull(pattern, "pattern must not be null")), actual -> actual == null
                        ? unsatisfied("string was null") : pattern.matcher(actual).matches() != negated ? satisfied(actual)
                                : unsatisfied(negated ? "string matched unexpected pattern" : "string did not match expected pattern"));
    }

}
