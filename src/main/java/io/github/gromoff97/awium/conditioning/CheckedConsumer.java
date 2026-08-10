package io.github.gromoff97.awium.conditioning;

@FunctionalInterface
public interface CheckedConsumer<T> {

    void accept(T value) throws Exception;
}
