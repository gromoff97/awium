package io.github.gromoff97.awium;

@FunctionalInterface
public interface ThrowingFunction<T, R> {

    R apply(T value) throws Exception;
}
