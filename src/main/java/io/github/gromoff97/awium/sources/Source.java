package io.github.gromoff97.awium.sources;

@FunctionalInterface
public interface Source<T> {

    T get() throws Exception;
}
