package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class ElementAtCondition extends SelectedCondition<SequencedCollection<?>> {
    ElementAtCondition(int index) {
        this("collection has an element at index " + index, index, false);
    }

    ElementAtCondition(boolean last) {
        this(last ? "collection has a last element" : "collection has a first element", 0, last);
    }

    private ElementAtCondition(String description, int index, boolean last) {
        super(description, null, actual -> selectElement(last && actual != null ? actual.reversed() : actual, index));
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
    }

    private static <ELEMENT> ConditionResult<ELEMENT> selectElement(SequencedCollection<? extends ELEMENT> actual, int index) {
        if (actual == null) return unsatisfied("collection was null");
        int position = 0;
        for (ELEMENT value : actual) {
            if (position++ == index) {
                return satisfied(value);
            }
        }
        return unsatisfied("collection size was " + position);
    }

}
