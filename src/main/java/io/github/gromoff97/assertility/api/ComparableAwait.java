package io.github.gromoff97.assertility.api;

public interface ComparableAwait<T extends Comparable<? super T>>
        extends ComparableTerminals<T, T> {
    ComparableTerminals<T, T> as(String description);

    ComparableTerminals<T, T> as(String format, Object... args);
}
