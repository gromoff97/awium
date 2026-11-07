package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;

import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record NullElementsCondition(Nulls nulls) implements PreservingCondition<Collection<?>> {
    enum Nulls {
        ANY("contains null", "did not contain null"),
        NONE("does not contain null", "contained null"),
        ONLY("contains only nulls", "did not contain only nulls");

        private final String description;
        private final String mismatch;

        Nulls(String description, String mismatch) {
            this.description = description;
            this.mismatch = mismatch;
        }
    }

    @Override
    public String description() {
        return "collection " + nulls.description;
    }

    @Override
    public AttemptResult<?> probe(Collection<?> actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matches;
        if (nulls == Nulls.ONLY) {
            matches = !actual.isEmpty() && matchesAll(actual, value -> value == null);
        } else {
            matches = matchesAny(actual, value -> value == null) != (nulls == Nulls.NONE);
        }
        if (matches) {
            return satisfied(actual);
        }
        return unsatisfied("collection " + nulls.mismatch);
    }
}
