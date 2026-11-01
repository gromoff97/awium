package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static io.github.gromoff97.awium.conditions.string.ExpectedText.text;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

record TextSetCondition(List<String> values, boolean negated) implements PreservingCondition<CharSequence> {
    TextSetCondition(Iterable<? extends CharSequence> expected, boolean negated) {
        this(texts(expected, negated ? "unexpected strings" : "expected strings"), negated);
    }

    @Override public String description() { return negated ? "string does not contain unexpected strings" : "string contains all expected strings"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", values)); }

    @Override
    public ConditionResult<?> probe(CharSequence actual) {
        if (actual == null) return unsatisfied("string was null");
        if (values.isEmpty()) return satisfied(actual);
        String text = actual.toString();
        boolean matches = negated ? values.stream().noneMatch(text::contains) : values.stream().allMatch(text::contains);
        return matches ? satisfied(actual)
                : unsatisfied(negated ? "string contained an unexpected string" : "string did not contain all expected strings");
    }

    private static List<String> texts(Iterable<? extends CharSequence> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false)
                .map(value -> text(value, "text")).toList();
    }
}
