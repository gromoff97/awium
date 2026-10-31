package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.MapEntries.entryMatches;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class HasEntryCondition<KEY, VALUE> extends PreservingCondition<Map<? super KEY, ? super VALUE>> {
    public HasEntryCondition(KEY key, VALUE value, boolean negated) {
        this(new SimpleImmutableEntry<>(key, value), negated);
    }

    private HasEntryCondition(Entry<KEY, VALUE> expected, boolean negated) {
        super(negated ? "map does not contain unexpected entry" : "map contains expected entry",
                new Reference<>(negated ? "Unexpected" : "Expected", expected), actual -> actual == null
                        ? unsatisfied("map was null")
                        : matchesAny(actual.entrySet(), entry -> entryMatches(entry, expected)) != negated
                                ? satisfied(actual) : unsatisfied(negated ? "map contained unexpected entry" : "map did not contain expected entry"));
    }

}
