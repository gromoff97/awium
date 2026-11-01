package io.github.gromoff97.awium.conditions.common.internal;

public enum SizeRelation {
    EQUAL("is"), NOT_EQUAL("is not"), GREATER("is greater than"), AT_LEAST("is at least"),
    LESS("is less than"), AT_MOST("is at most"), BETWEEN("is between");

    private final String description;

    SizeRelation(String description) { this.description = description; }

    String description(int bound, int upper) {
        return description + " " + bound + (this == BETWEEN ? " and " + upper : "");
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
