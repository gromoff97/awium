package io.github.gromoff97.assertility;

final class StructuralConditions {

    enum Relation {
        EMPTY,
        NON_EMPTY,
        EXACTLY,
        NOT_EXACTLY,
        GREATER_THAN,
        AT_LEAST,
        LESS_THAN,
        AT_MOST;

        boolean matches(int size, int bound) {
            return switch (this) {
                case EMPTY -> size == 0;
                case NON_EMPTY -> size > 0;
                case EXACTLY -> size == bound;
                case NOT_EXACTLY -> size != bound;
                case GREATER_THAN -> size > bound;
                case AT_LEAST -> size >= bound;
                case LESS_THAN -> size < bound;
                case AT_MOST -> size <= bound;
            };
        }

        String description(String subject, int bound) {
            return subject + switch (this) {
                case EMPTY -> " to be empty";
                case NON_EMPTY -> " to be non-empty";
                case EXACTLY -> " size to be exactly " + bound;
                case NOT_EXACTLY -> " size not to be exactly " + bound;
                case GREATER_THAN -> " size to be greater than " + bound;
                case AT_LEAST -> " size to be at least " + bound;
                case LESS_THAN -> " size to be less than " + bound;
                case AT_MOST -> " size to be at most " + bound;
            };
        }

        String mismatch(String subject, int size) {
            return switch (this) {
                case EMPTY -> subject + " was non-empty";
                case NON_EMPTY -> subject + " was empty";
                default -> subject + " size was " + size;
            };
        }
    }

    private StructuralConditions() {
    }

    static StructuralCondition empty() {
        return new StructuralCondition(Relation.EMPTY, 0);
    }

    static StructuralCondition nonEmpty() {
        return new StructuralCondition(Relation.NON_EMPTY, 0);
    }

    static StructuralCondition sizeExactly(int expected) {
        return sized(Relation.EXACTLY, expected);
    }

    static StructuralCondition sizeNotExactly(int unexpected) {
        return sized(Relation.NOT_EXACTLY, unexpected);
    }

    static StructuralCondition sizeGreaterThan(int lowerBound) {
        return sized(Relation.GREATER_THAN, lowerBound);
    }

    static StructuralCondition sizeAtLeast(int lowerBound) {
        return sized(Relation.AT_LEAST, lowerBound);
    }

    static StructuralCondition sizeLessThan(int upperBound) {
        return sized(Relation.LESS_THAN, upperBound);
    }

    static StructuralCondition sizeAtMost(int upperBound) {
        return sized(Relation.AT_MOST, upperBound);
    }

    private static StructuralCondition sized(Relation relation, int bound) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        return new StructuralCondition(relation, bound);
    }
}
