package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class SortedCondition<ELEMENT> extends PreservingCondition<SequencedCollection<? extends ELEMENT>> {
    public SortedCondition(Comparator<? super ELEMENT> comparator) {
        super("collection is sorted", null, actual -> checkOrder(actual, comparator));
        requireNonNull(comparator, "comparator must not be null");
    }

    private static <ELEMENT> ConditionResult<SequencedCollection<ELEMENT>> checkOrder(SequencedCollection<ELEMENT> actual,
            Comparator<? super ELEMENT> comparator) {
        if (actual == null) return unsatisfied("collection was null");
        Iterator<ELEMENT> values = actual.iterator();
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
