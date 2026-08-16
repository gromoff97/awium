package io.github.gromoff97.awium.conditioning;

@FunctionalInterface
public interface CheckedPredicate<T> {

    boolean test(T value) throws Exception;
}
