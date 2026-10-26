package io.github.gromoff97.awium;

import static java.util.Objects.requireNonNull;

/** A boolean check that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedPredicate<VALUE> {

    boolean test(VALUE value) throws Throwable;

    /** Negates a check without changing its input type or intercepting a thrown failure. */
    static <VALUE> CheckedPredicate<VALUE> not(CheckedPredicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return value -> !predicate.test(value);
    }
}
