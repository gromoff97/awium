package io.github.gromoff97.awium.conditioning;

@FunctionalInterface
public interface CheckedBiPredicate<T, U> {

    boolean test(T first, U second) throws Exception;
}
