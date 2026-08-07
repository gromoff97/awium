package io.github.gromoff97.assertility.api;

public interface ComparableTerminals<T extends Comparable<? super T>, R>
        extends ObjectTerminals<T, R> {
    R isGreaterThan(T expected);

    R isGreaterThanOrEqualTo(T expected);

    R isLessThan(T expected);

    R isLessThanOrEqualTo(T expected);
}
