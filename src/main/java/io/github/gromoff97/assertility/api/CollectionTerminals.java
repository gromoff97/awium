package io.github.gromoff97.assertility.api;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

public interface CollectionTerminals<E, C extends Collection<E>, RC, RE, RL>
        extends ObjectTerminals<C, RC> {
    RC isEmpty();

    RC isNotEmpty();

    RC hasSize(int size);

    RC hasSizeGreaterThan(int size);

    RC hasSizeGreaterThanOrEqualTo(int size);

    RC hasSizeLessThan(int size);

    RC hasSizeLessThanOrEqualTo(int size);

    RE single();

    RE single(Predicate<? super E> predicate);

    RE single(String description, Predicate<? super E> predicate);

    <V> RE single(Function<? super E, ? extends V> extractor, V expected);

    RE any();

    RE any(Predicate<? super E> predicate);

    RE any(String description, Predicate<? super E> predicate);

    <V> RE any(Function<? super E, ? extends V> extractor, V expected);

    RL exactly(int count, Predicate<? super E> predicate);

    RL exactly(int count, String description, Predicate<? super E> predicate);

    <V> RL exactly(int count, Function<? super E, ? extends V> extractor, V expected);
}
