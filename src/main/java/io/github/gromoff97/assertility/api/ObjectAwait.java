package io.github.gromoff97.assertility.api;

public interface ObjectAwait<T> extends ObjectTerminals<T, T> {
    ObjectTerminals<T, T> as(String description);

    ObjectTerminals<T, T> as(String format, Object... args);
}
