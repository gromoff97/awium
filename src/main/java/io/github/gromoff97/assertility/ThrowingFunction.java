package io.github.gromoff97.assertility;

@FunctionalInterface
public interface ThrowingFunction<T, R> {

    R apply(T value) throws Exception;
}
