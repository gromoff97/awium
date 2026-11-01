package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record BoundCondition<BOUND, INPUT extends Comparable<? super BOUND>>(
        Relation relation, BOUND bound) implements PreservingCondition<INPUT> {
    enum Relation {
        GREATER_THAN("greater than"), AT_LEAST("at least"), LESS_THAN("less than"), AT_MOST("at most");
        private final String label;
        Relation(String label) { this.label = label; }
        boolean accepts(int comparison) {
            return switch (this) {
                case GREATER_THAN -> comparison > 0;
                case AT_LEAST -> comparison >= 0;
                case LESS_THAN -> comparison < 0;
                case AT_MOST -> comparison <= 0;
            };
        }
    }

    BoundCondition { requireNonNull(bound, "bound must not be null"); }

    @Override public String description() { return "value is " + relation.label + " the bound"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", bound)); }

    @Override
    public ConditionResult<?> probe(INPUT actual) {
        return actual == null ? unsatisfied("value was null") : relation.accepts(actual.compareTo(bound))
                ? satisfied(actual) : unsatisfied("value was not " + relation.label + " the bound");
    }
}
