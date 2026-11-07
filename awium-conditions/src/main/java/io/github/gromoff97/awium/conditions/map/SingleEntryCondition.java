package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record SingleEntryCondition() implements SelectedCondition<Map<?, ?>> {
    @Override public String description() { return "map has a single entry"; }
    @Override public AttemptResult<?> probe(Map<?, ?> actual) throws Throwable {
        if (actual == null) return unsatisfied("map was null");
        var result = singleElement.probe(actual.entrySet());
        if (result instanceof Unsatisfied<?> failure) {
            return unsatisfied(failure.diagnostic().withMismatch("map entries: " + failure.mismatch()), failure.cause());
        }
        return result;
    }
}
