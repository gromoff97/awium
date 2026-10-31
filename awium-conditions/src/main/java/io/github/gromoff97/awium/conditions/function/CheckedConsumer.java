package io.github.gromoff97.awium.conditions.function;

@FunctionalInterface
public interface CheckedConsumer<VALUE> {

    void accept(VALUE value) throws Throwable;
}
