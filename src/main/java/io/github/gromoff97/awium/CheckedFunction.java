package io.github.gromoff97.awium;

/** A condition callback that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedFunction<VALUE, RESULT> {

    RESULT apply(VALUE value) throws Throwable;
}
