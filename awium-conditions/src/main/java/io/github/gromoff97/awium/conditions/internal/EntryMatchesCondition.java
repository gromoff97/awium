package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import java.util.Map.Entry;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class EntryMatchesCondition<KEY, VALUE> extends PreservingCondition<Entry<? extends KEY, ? extends VALUE>> {
    public EntryMatchesCondition(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        super("value matches", null, entry -> predicate.test(entry.getKey(), entry.getValue())
                ? satisfied(entry) : unsatisfied("value did not match"));
        requireNonNull(predicate, "predicate must not be null");
    }

}
