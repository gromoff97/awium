package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

record ContainsCondition<ELEMENT>(ELEMENT expected, boolean negated) implements PreservingCondition<Collection<? super ELEMENT>> {
    @Override public String description() { return negated ? "collection does not contain unexpected element" : "collection contains expected element"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", expected)); }
    @Override public AttemptResult<?> probe(Collection<? super ELEMENT> actual) {
        return actual == null ? unsatisfied("collection was null")
                : matchesAny(actual, value -> deepEquals(value, expected)) != negated ? satisfied(actual)
                : unsatisfied(negated ? "collection contained unexpected element" : "collection did not contain expected element");
    }
}
