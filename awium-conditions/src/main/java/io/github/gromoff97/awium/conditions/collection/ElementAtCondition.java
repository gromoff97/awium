package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record ElementAtCondition(int index, Position position) implements SelectedCondition<SequencedCollection<?>> {
    enum Position { FIRST, LAST, INDEX }

    ElementAtCondition { if (index < 0) throw new IllegalArgumentException("index must be non-negative"); }
    ElementAtCondition(int index) {
        this(index, Position.INDEX);
    }

    ElementAtCondition(Position position) {
        this(0, position);
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
    public AttemptResult<?> probe(SequencedCollection<?> actual) {
        if (position == Position.LAST && actual != null) {
            return selectElement(actual.reversed(), index);
        }
        return selectElement(actual, index);
    }

    private static <ELEMENT> AttemptResult<ELEMENT> selectElement(SequencedCollection<? extends ELEMENT> actual, int index) {
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
