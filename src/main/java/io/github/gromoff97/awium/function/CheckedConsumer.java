package io.github.gromoff97.awium.function;

@FunctionalInterface
public interface CheckedConsumer<VALUE> {

    void accept(VALUE value) throws Throwable;
}
