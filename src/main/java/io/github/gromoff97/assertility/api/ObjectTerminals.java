package io.github.gromoff97.assertility.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface ObjectTerminals<T, R> {
    R isNull();

    R isNotNull();

    R isEqualTo(T expected);

    R isNotEqualTo(T expected);

    <V> R returns(V expected, Function<? super T, ? extends V> extractor);

    R matches(Predicate<? super T> predicate);

    R matches(String description, Predicate<? super T> predicate);

    R satisfies(Consumer<? super T> assertion);
}
