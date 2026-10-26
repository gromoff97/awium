package io.github.gromoff97.awium;

/** A boolean check that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedPredicate<VALUE> {

    boolean test(VALUE value) throws Throwable;
}
