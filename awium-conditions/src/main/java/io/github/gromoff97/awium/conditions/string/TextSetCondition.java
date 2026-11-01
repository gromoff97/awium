package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.List;

import static io.github.gromoff97.awium.support.ExpectedValues.text;
import static io.github.gromoff97.awium.support.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.support.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

final class TextSetCondition extends PreservingCondition<CharSequence> {
    TextSetCondition(Iterable<? extends CharSequence> expected, boolean negated) {
        this(texts(expected, negated ? "unexpected strings" : "expected strings"), negated);
    }

    private TextSetCondition(List<String> values, boolean negated) {
        super(negated ? "string does not contain unexpected strings" : "string contains all expected strings",
                new Reference<>(negated ? "Unexpected" : "Expected", values), actual -> actual == null
                        ? unsatisfied("string was null")
                        : values.isEmpty() || (negated ? !matchesAny(values, actual.toString()::contains) : matchesAll(values, actual.toString()::contains))
                                ? satisfied(actual) : unsatisfied(negated ? "string contained an unexpected string" : "string did not contain all expected strings"));
    }

    private static List<String> texts(Iterable<? extends CharSequence> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false)
                .map(value -> text(value, "text")).toList();
    }

}
