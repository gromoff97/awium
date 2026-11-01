package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.function.AwaitAttempt;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public enum SizeRelation {
    EQUAL("is"), NOT_EQUAL("is not"), GREATER("is greater than"), AT_LEAST("is at least"),
    LESS("is less than"), AT_MOST("is at most"), BETWEEN("is between");

    private final String description;

    SizeRelation(String description) { this.description = description; }

    public String description(int bound, int upper) {
        return description + " " + bound + (this == BETWEEN ? " and " + upper : "");
    }

    public <INPUT> AwaitAttempt<INPUT, INPUT> check(String subject, String measure,
            ToIntFunction<? super INPUT> sizeOf, int lower, int upper) {
        if (this == BETWEEN && (lower < 0 || upper < lower)) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
        if (lower < 0) throw new IllegalArgumentException(measure + " must be non-negative");
        return actual -> {
            if (actual == null) return unsatisfied(subject + " was null");
            int size = sizeOf.applyAsInt(actual);
            return matches(size, lower, upper) ? satisfied(actual) : unsatisfied(subject + " " + measure + " was " + size);
        };
    }

    public boolean matches(int actual, int bound, int upper) {
        return switch (this) {
            case EQUAL -> actual == bound;
            case NOT_EQUAL -> actual != bound;
            case GREATER -> actual > bound;
            case AT_LEAST -> actual >= bound;
            case LESS -> actual < bound;
            case AT_MOST -> actual <= bound;
            case BETWEEN -> actual >= bound && actual <= upper;
        };
    }
}
