package io.github.gromoff97.assertility;

@FunctionalInterface
public interface ThrowingConsumer<T> {

    void accept(T value) throws Exception;
}
