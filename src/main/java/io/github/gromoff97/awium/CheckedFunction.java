package io.github.gromoff97.awium;

/** A condition callback that may propagate a checked exception to the wait's failure handling. */
@FunctionalInterface
public interface CheckedFunction<Value, Result> {

    Result apply(Value value) throws Exception;
}
