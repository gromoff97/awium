package io.github.gromoff97.assertility.api;

public interface StringAwait extends StringTerminals<String> {
    StringTerminals<String> as(String description);

    StringTerminals<String> as(String format, Object... args);
}
