package io.github.gromoff97.assertility.api;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public interface OptionalTerminals<T, RS, RV> extends ObjectTerminals<Optional<T>, RS> {
    RS isEmpty();

    RV isPresent();

    RV isPresent(Predicate<? super T> predicate);

    RV isPresent(String description, Predicate<? super T> predicate);

    RV contains(T expected);

    <V> RV contains(V expected, Function<? super T, ? extends V> extractor);
}
