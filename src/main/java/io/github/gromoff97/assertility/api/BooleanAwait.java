package io.github.gromoff97.assertility.api;

public interface BooleanAwait extends BooleanTerminals<Boolean> {
    BooleanTerminals<Boolean> as(String description);

    BooleanTerminals<Boolean> as(String format, Object... args);
}
