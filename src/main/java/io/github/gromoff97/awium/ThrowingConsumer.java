package io.github.gromoff97.awium;

@FunctionalInterface
public interface ThrowingConsumer<T> {

    void accept(T value) throws Exception;
}
