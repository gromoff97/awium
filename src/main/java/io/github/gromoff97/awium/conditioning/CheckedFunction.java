package io.github.gromoff97.awium.conditioning;

@FunctionalInterface
public interface CheckedFunction<T, R> {

    R apply(T value) throws Exception;
}
