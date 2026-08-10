package io.github.gromoff97.awium;

@FunctionalInterface
interface Parker {

    void parkNanos(long nanos);
}
