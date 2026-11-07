package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
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

    @Override
    public String description() {
        if (negated) {
            return "string does not match unexpected pattern";
        }
        return "string matches expected pattern";
    }

    @Override
    public List<Reference<?>> references() {
        if (negated) {
            return List.of(new Reference<>("Unexpected", pattern));
        }
        return List.of(new Reference<>("Expected", pattern));
    }

    @Override
    public AttemptResult<?> probe(CharSequence actual) {
        if (actual == null) return unsatisfied("string was null");
        if (pattern.matcher(actual).matches() != negated) {
            return satisfied(actual);
        }
        if (negated) {
            return unsatisfied("string matched unexpected pattern");
        }
        return unsatisfied("string did not match expected pattern");
    }
}
