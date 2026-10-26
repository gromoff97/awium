package io.github.gromoff97.awium;

import static java.util.Objects.requireNonNull;

/** A two-value boolean check that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedBiPredicate<FIRST, SECOND> {

    boolean test(FIRST first, SECOND second) throws Throwable;

    /** Negates a check without changing its input types or intercepting a thrown failure. */
    static <FIRST, SECOND> CheckedBiPredicate<FIRST, SECOND> not(
            CheckedBiPredicate<? super FIRST, ? super SECOND> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return (first, second) -> !predicate.test(first, second);
    }
}
