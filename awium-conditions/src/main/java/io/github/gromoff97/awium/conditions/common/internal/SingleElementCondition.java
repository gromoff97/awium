package io.github.gromoff97.awium.conditions.common.internal;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Collection;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class SingleElementCondition extends SelectedCondition<Collection<?>> {
    public SingleElementCondition() {
        super("collection has a single element", null, actual -> single("collection", actual));
    }

    public static <ELEMENT> ConditionResult<ELEMENT> single(String subject, Iterable<? extends ELEMENT> values) {
        if (values == null) return unsatisfied(subject + " was null");
        var iterator = values.iterator();
        if (!iterator.hasNext()) return unsatisfied(subject + " size was 0");
        ELEMENT value = iterator.next();
        return iterator.hasNext() ? unsatisfied(subject + " size was greater than 1") : satisfied(value);
    }

}
