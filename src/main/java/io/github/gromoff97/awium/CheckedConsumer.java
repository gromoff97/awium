package io.github.gromoff97.awium;

/** An assertion callback that may propagate a checked exception to the wait's failure handling. */
@FunctionalInterface
public interface CheckedConsumer<Value> {

    void accept(Value value) throws Exception;
}
