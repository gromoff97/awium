package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

record ElementAtCondition(int index, Position position) implements SelectedCondition<SequencedCollection<?>> {
    enum Position { FIRST, LAST, INDEX }

    ElementAtCondition { if (index < 0) throw new IllegalArgumentException("index must be non-negative"); }
    ElementAtCondition(int index) {
        this(index, Position.INDEX);
    }

    ElementAtCondition(boolean last) {
        this(0, last ? Position.LAST : Position.FIRST);
    }

    @Override
    public String description() {
        return switch (position) {
            case FIRST -> "collection has a first element";
            case LAST -> "collection has a last element";
            case INDEX -> "collection has an element at index " + index;
        };
    }

    @Override
    public ConditionResult<?> probe(SequencedCollection<?> actual) {
        return selectElement(position == Position.LAST && actual != null ? actual.reversed() : actual, index);
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
