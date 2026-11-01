package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditions.collection.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.prefixMatches;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

record CollectionSequenceCondition<INPUT extends SequencedCollection<?>>(List<?> expected, Sequence sequence, boolean negated) implements PreservingCondition<INPUT> {
    enum Sequence { PREFIX, SUFFIX, SEQUENCE, SUBSEQUENCE }

    CollectionSequenceCondition(Iterable<?> expected, Sequence sequence, boolean negated) {
        this(snapshot(expected, negated ? "unexpected elements" : "expected elements"), sequence, negated);
    }

    @Override public String description() { return "collection " + (negated ? "does not contain unexpected " : "contains expected ") + sequence.name().toLowerCase(java.util.Locale.ROOT); }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", expected)); }

    @Override
    public AttemptResult<?> probe(INPUT actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matched = switch (sequence) {
            case PREFIX -> expected.isEmpty() || actual.size() >= expected.size() && prefixMatches(actual.iterator(), expected);
            case SUFFIX -> expected.isEmpty() || actual.size() >= expected.size() && prefixMatches(actual.reversed().iterator(), expected.reversed());
            case SEQUENCE -> containsSequence(actual, expected);
            case SUBSEQUENCE -> containsSubsequence(actual, expected);
        };
        return matched != negated ? satisfied(actual) : unsatisfied("collection "
                + (negated ? "contained unexpected " : "did not contain expected ") + sequence.name().toLowerCase(java.util.Locale.ROOT));
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
