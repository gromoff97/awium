package io.github.gromoff97.awium;

/** An assertion callback that may propagate any throwable to the wait's failure handling. */
@FunctionalInterface
public interface CheckedConsumer<VALUE> {

    void accept(VALUE value) throws Throwable;
}
