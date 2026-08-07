package io.github.gromoff97.assertility.api;

import java.util.Optional;

public interface OptionalAwait<T> extends OptionalTerminals<T, Optional<T>, T> {
    OptionalTerminals<T, Optional<T>, T> as(String description);

    OptionalTerminals<T, Optional<T>, T> as(String format, Object... args);
}
