package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

record SingleEntryCondition() implements SelectedCondition<Map<?, ?>> {
    @Override public String description() { return "map has a single entry"; }
    @Override public ConditionResult<?> probe(Map<?, ?> actual) throws Throwable {
        if (actual == null) return unsatisfied("map was null");
        var result = singleElement.probe(actual.entrySet());
        return result instanceof Unsatisfied<?> failure ? unsatisfied(failure.diagnostic().withMismatch("map entries: " + failure.mismatch()), failure.cause()) : result;
    }
}
