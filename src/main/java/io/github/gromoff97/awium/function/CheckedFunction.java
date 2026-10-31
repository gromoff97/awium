package io.github.gromoff97.awium.function;

@FunctionalInterface
public interface CheckedFunction<VALUE, RESULT> {

    RESULT apply(VALUE value) throws Throwable;
}
