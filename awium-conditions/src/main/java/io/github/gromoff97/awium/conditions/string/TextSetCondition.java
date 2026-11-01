package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.conditions.string.ExpectedText.text;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

record TextSetCondition(List<String> values, boolean negated) implements PreservingCondition<CharSequence> {
    TextSetCondition(Iterable<? extends CharSequence> expected, boolean negated) {
        this(texts(expected, negated), negated);
    }

    @Override
    public String description() {
        if (negated) {
            return "string does not contain unexpected strings";
        }
        return "string contains all expected strings";
    }

    @Override
    public List<Reference<?>> references() {
        if (negated) {
            return List.of(new Reference<>("Unexpected", values));
        }
        return List.of(new Reference<>("Expected", values));
    }

    @Override
    public AttemptResult<?> probe(CharSequence actual) {
        if (actual == null) return unsatisfied("string was null");
        if (values.isEmpty()) return satisfied(actual);
        String text = actual.toString();
        if (negated) {
            if (values.stream().noneMatch(text::contains)) {
                return satisfied(actual);
            }
            return unsatisfied("string contained an unexpected string");
        }
        if (values.stream().allMatch(text::contains)) {
            return satisfied(actual);
        }
        return unsatisfied("string did not contain all expected strings");
    }

    private static List<String> texts(Iterable<? extends CharSequence> values, boolean negated) {
        if (negated) {
            requireNonNull(values, "unexpected strings must not be null");
        } else {
            requireNonNull(values, "expected strings must not be null");
        }
        return stream(values.spliterator(), false)
                .map(value -> text(value, "text")).toList();
    }
}
