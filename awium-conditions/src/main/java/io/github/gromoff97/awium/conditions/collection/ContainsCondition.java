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
    @Override
    public String description() {
        if (negated) {
            return "collection does not contain unexpected element";
        }
        return "collection contains expected element";
    }

    @Override
    public List<Reference<?>> references() {
        if (negated) {
            return List.of(new Reference<>("Unexpected", expected));
        }
        return List.of(new Reference<>("Expected", expected));
    }

    @Override public AttemptResult<?> probe(Collection<? super ELEMENT> actual) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        if (matchesAny(actual, value -> deepEquals(value, expected)) != negated) {
            return satisfied(actual);
        }
        if (negated) {
            return unsatisfied("collection contained unexpected element");
        }
        return unsatisfied("collection did not contain expected element");
    }
}
