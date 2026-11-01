package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditions.internal.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.prefixMatches;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class CollectionSequenceCondition<INPUT extends SequencedCollection<?>> extends PreservingCondition<INPUT> {
    public enum Sequence { PREFIX, SUFFIX, SEQUENCE, SUBSEQUENCE }

    public CollectionSequenceCondition(Iterable<?> expected, Sequence sequence, boolean negated) {
        this(snapshot(expected, negated ? "unexpected elements" : "expected elements"), sequence, negated);
    }

    private CollectionSequenceCondition(List<?> expected, Sequence sequence, boolean negated) {
        super("collection " + (negated ? "does not contain unexpected " : "contains expected ") + sequence.name().toLowerCase(java.util.Locale.ROOT),
                new Reference<>(negated ? "Unexpected" : "Expected", expected), actual -> {
            if (actual == null) return unsatisfied("collection was null");
            boolean matched = switch (sequence) {
                case PREFIX -> expected.isEmpty() || actual.size() >= expected.size() && prefixMatches(actual.iterator(), expected);
                case SUFFIX -> expected.isEmpty() || actual.size() >= expected.size() && prefixMatches(actual.reversed().iterator(), expected.reversed());
                case SEQUENCE -> containsSequence(actual, expected);
                case SUBSEQUENCE -> containsSubsequence(actual, expected);
            };
            return matched != negated ? satisfied(actual) : unsatisfied("collection "
                    + (negated ? "contained unexpected " : "did not contain expected ") + sequence.name().toLowerCase(java.util.Locale.ROOT));
        });
    }

    private static boolean containsSequence(SequencedCollection<?> actual, List<?> expected) {
        if (expected.isEmpty()) return true;
        if (actual.size() < expected.size()) return false;
        var window = new LinkedList<Object>();
        for (Object value : actual) {
            window.addLast(value);
            if (window.size() < expected.size()) continue;
            if (prefixMatches(window.iterator(), expected)) return true;
            window.removeFirst();
        }
        return false;
    }

    private static boolean containsSubsequence(Collection<?> actual, List<?> expected) {
        if (expected.isEmpty()) return true;
        if (actual.size() < expected.size()) return false;
        int expectedIndex = 0;
        for (Object value : actual) {
            if (deepEquals(value, expected.get(expectedIndex)) && ++expectedIndex == expected.size()) {
                return true;
            }
        }
        return false;
    }

}
