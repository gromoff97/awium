package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record SortedCondition<ELEMENT>(Comparator<? super ELEMENT> comparator) implements PreservingCondition<SequencedCollection<? extends ELEMENT>> {
    SortedCondition { requireNonNull(comparator, "comparator must not be null"); }
    @Override public String description() { return "collection is sorted"; }

    @Override public ConditionResult<?> probe(SequencedCollection<? extends ELEMENT> actual) {
        if (actual == null) return unsatisfied("collection was null");
        Iterator<? extends ELEMENT> values = actual.iterator();
        if (!values.hasNext()) {
            return satisfied(actual);
        }
        ELEMENT previous = values.next();
        long index = 1;
        while (values.hasNext()) {
            ELEMENT next = values.next();
            if (comparator.compare(previous, next) > 0) {
                return unsatisfied("collection was not sorted at indices " + (index - 1) + " and " + index);
            }
            previous = next;
            index++;
        }
        return satisfied(actual);
    }
}
