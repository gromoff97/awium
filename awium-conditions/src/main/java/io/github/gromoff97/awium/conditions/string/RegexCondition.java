package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.conditions.string.ExpectedText.text;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.compile;

record RegexCondition(Pattern pattern, boolean negated) implements PreservingCondition<CharSequence> {
    RegexCondition { requireNonNull(pattern, "pattern must not be null"); }
    RegexCondition(CharSequence regex, boolean negated) {
        this(compile(text(regex, "regex")), negated);
    }

    @Override public String description() { return negated ? "string does not match unexpected pattern" : "string matches expected pattern"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", pattern)); }

    @Override
    public AttemptResult<?> probe(CharSequence actual) {
        if (actual == null) return unsatisfied("string was null");
        return pattern.matcher(actual).matches() != negated ? satisfied(actual)
                : unsatisfied(negated ? "string matched unexpected pattern" : "string did not match expected pattern");
    }
}
