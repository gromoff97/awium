package io.github.gromoff97.awium;

/** A two-value boolean check that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedBiPredicate<FIRST, SECOND> {

    boolean test(FIRST first, SECOND second) throws Throwable;
}
