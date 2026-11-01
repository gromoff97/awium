package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.regex.Pattern;

import static io.github.gromoff97.awium.conditions.string.ExpectedText.text;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.regex.Pattern.compile;

record ContainsIgnoringCaseCondition(Pattern pattern) implements PreservingCondition<CharSequence> {
    ContainsIgnoringCaseCondition(CharSequence expected) {
        this(compile(text(expected, "expected string"), Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    @Override public String description() { return "string contains expected string ignoring case"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", pattern.pattern())); }

    @Override
    public AttemptResult<?> probe(CharSequence actual) throws Throwable {
        if (actual == null) return unsatisfied("string was null");
        return pattern.matcher(actual).find() ? satisfied(actual)
                : unsatisfied("string did not contain expected string ignoring case");
    }
}
